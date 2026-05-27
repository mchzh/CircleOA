/**
 * Test harness for ScheduleCallCenter.
 *
 * Spec verified results:
 *   schedule(2, 1, 0, SAMPLE) → "114005,OR Other,WA Other"
 *   schedule(2, 2, 1, SAMPLE) → "133559,WA King,WA Other"
 *   schedule(3, 3, 2, SAMPLE) → "114005,OR Other,WA King,WA Other"
 *
 * DAG shapes covered:
 *   SAMPLE        — two independent chains  (A→C, B→D)
 *   DIAMOND       — node with TWO parents   (A→C, B→C, C→D)
 *   WIDE_CHAIN    — long serial chain       (A→B→C→D→E)
 *   PARALLEL_ONLY — no dependencies at all
 *   MIXED         — mix of chains, fan-in, fan-out
 */
public class Main {

    // ─────────────────────────────────────────────────────────────────────────
    // Dataset 1 — original spec sample
    //
    //   Home/OR Jefferson (5444)
    //   Medicare/OR Lake  (1304) ──► Life/OR Other  (12806)
    //   Medicare/WA King  (43061)──► Life/WA Other  (70944)
    //
    // Two independent chains; no fan-in.
    // ─────────────────────────────────────────────────────────────────────────
    static final String SAMPLE =
        "Home,OR Jefferson,5444;"
      + "Medicare,OR Lake,1304;"
      + "Medicare,WA King,43061;"
      + "Life,OR Other,12806,Medicare,OR Lake;"
      + "Life,WA Other,70944,Medicare,WA King";

    // ─────────────────────────────────────────────────────────────────────────
    // Dataset 2 — DIAMOND: node with TWO parent dependencies
    //
    //   Intake/WA North  (1000) ─┐
    //                             ├──► Review/WA Central (5000) ──► Close/WA South (2000)
    //   Intake/OR West   (3000) ─┘
    //
    //   Review/WA Central can only start after BOTH parents finish.
    //   With N=2 workers:
    //     t=0:    run Intake/WA North(1000) + Intake/OR West(3000)
    //     t=1000: WA North done (1 worker free), but Review needs OR West too → waits
    //     t=3000: OR West done  → Review/WA Central UNBLOCKED
    //     t=3000: start Review/WA Central (5000) → finishes @ 8000
    //     t=8000: start Close/WA South (2000)    → finishes @ 10000
    //   completion = 10000
    // ─────────────────────────────────────────────────────────────────────────
    static final String DIAMOND =
        "Intake,WA North,1000;"
      + "Intake,OR West,3000;"
      + "Review,WA Central,5000,Intake,WA North,Intake,OR West;"   // ← TWO parents
      + "Close,WA South,2000,Review,WA Central";

    // ─────────────────────────────────────────────────────────────────────────
    // Dataset 3 — WIDE_CHAIN: strict serial pipeline (N workers don't help)
    //
    //   Prep/A (500) → Process/B (1500) → Validate/C (800) → Approve/D (1200) → Ship/E (300)
    //
    //   With any N, only one task runs at a time (each depends on previous).
    //   completion = 500+1500+800+1200+300 = 4300
    // ─────────────────────────────────────────────────────────────────────────
    static final String WIDE_CHAIN =
        "Prep,A,500;"
      + "Process,B,1500,Prep,A;"
      + "Validate,C,800,Process,B;"
      + "Approve,D,1200,Validate,C;"
      + "Ship,E,300,Approve,D";

    // ─────────────────────────────────────────────────────────────────────────
    // Dataset 4 — PARALLEL_ONLY: all independent, no dependencies
    //
    //   Task/G1(200)  Task/G2(500)  Task/G3(300)  Task/G4(800)
    //
    //   N=4: all run at t=0 → completion = 800 (longest)
    //   N=2: t=0   run G4(800)+G2(500)
    //        t=500: G2 done → G3(300) starts → finishes @800
    //        t=800: G4+G3 both done → G1(200) starts → finishes @1000
    //        completion = 1000
    //   N=1: t=0   G4(800) → t=800 G2(500) → t=1300 G3(300) → t=1600 G1(200)
    //        completion = 1800
    // ─────────────────────────────────────────────────────────────────────────
    static final String PARALLEL_ONLY =
        "Task,G1,200;"
      + "Task,G2,500;"
      + "Task,G3,300;"
      + "Task,G4,800";

    // ─────────────────────────────────────────────────────────────────────────
    // Dataset 5 — MIXED: fan-out + fan-in + independent node
    //
    //   Setup/Root (100) ──► Work/Alpha (400)─┐
    //                   └──► Work/Beta  (600)─┴──► Final/Done (200)
    //   Audit/Side  (900)   [independent]
    //
    //   Critical paths:
    //     Audit/Side  = 900
    //     Setup/Root  = 100+600+200 = 900 (via Beta)
    //     Work/Beta   = 600+200 = 800
    //     Work/Alpha  = 400+200 = 600
    //     Final/Done  = 200
    //
    //   N=3: t=0   run Audit(900)+Setup/Root(100) [highest crit paths]
    //        t=100: Root done → Alpha(600)+Beta(800) unblocked, start both
    //        t=500: Alpha done → waits for Beta (Final needs both parents)
    //        t=700: Beta done → Final/Done UNBLOCKED → start Final(200)
    //        t=900: Final done + Audit done simultaneously → completion = 900
    //
    //   N=1: t=0   Audit(900) [highest crit path]
    //        t=900: Setup/Root(100) → t=1000: Beta(600) → t=1600: Alpha(400) wait no...
    //        t=1000: Beta(600) → t=1600 done
    //        t=1600: Alpha(400) → but Alpha also needs Root which is done already
    //        Actually: Root→Alpha unblocked at 1100, Root→Beta unblocked at 1100
    //        t=900:  Audit done → Setup/Root starts
    //        t=1000: Root done → Beta(800 crit) starts
    //        t=1600: Beta done → Final needs Alpha too, Alpha still waiting
    //        t=1600: Alpha(600 crit) starts
    //        t=2000: Alpha done → Final/Done(200) unblocked
    //        t=2200: Final done → completion = 2200
    // ─────────────────────────────────────────────────────────────────────────
    static final String MIXED =
        "Setup,Root,100;"
      + "Work,Alpha,400,Setup,Root;"
      + "Work,Beta,600,Setup,Root;"
      + "Final,Done,200,Work,Alpha,Work,Beta;"  // ← TWO parents (fan-in)
      + "Audit,Side,900";                        // ← independent node

    // ─────────────────────────────────────────────────────────────────────────
    public static void main(String[] args) {

        sep("DATASET 1 — Spec Sample (two independent chains)");
        print("N=0 unlimited", ScheduleCallCenter.schedule(2, 1, 0, SAMPLE));
        print("N=1 single",    ScheduleCallCenter.schedule(2, 2, 1, SAMPLE));
        print("N=2 workers",   ScheduleCallCenter.schedule(3, 3, 2, SAMPLE));
        print("G=0 time only", ScheduleCallCenter.schedule(0, 0, 0, SAMPLE));

        sep("DATASET 2 — Diamond: node with TWO parents (fan-in)");
        //
        //   Intake/WA North (1000) ─┐
        //                            ├──► Review/WA Central (5000) ──► Close/WA South (2000)
        //   Intake/OR West  (3000) ─┘
        //
        print("N=2  expected=10000", ScheduleCallCenter.schedule(2, 2, 2, DIAMOND));
        print("N=1  expected=11000", ScheduleCallCenter.schedule(2, 2, 1, DIAMOND));
        print("N=0  expected=10000", ScheduleCallCenter.schedule(2, 2, 0, DIAMOND));
        print("G=0  expected=10000", ScheduleCallCenter.schedule(0, 0, 2, DIAMOND));

        sep("DATASET 3 — Wide Chain: strict serial pipeline");
        //   Prep→Process→Validate→Approve→Ship   total = 4300
        print("N=1  expected=4300", ScheduleCallCenter.schedule(2, 2, 1, WIDE_CHAIN));
        print("N=5  expected=4300", ScheduleCallCenter.schedule(2, 2, 5, WIDE_CHAIN));
        print("N=0  expected=4300", ScheduleCallCenter.schedule(2, 2, 0, WIDE_CHAIN));

        sep("DATASET 4 — Parallel Only: no dependencies");
        //   G4(800) is longest → completion=800 with N>=2
        print("N=4  expected=800",  ScheduleCallCenter.schedule(1, 1, 4, PARALLEL_ONLY));
        print("N=1  expected=1800", ScheduleCallCenter.schedule(1, 1, 1, PARALLEL_ONLY));
        print("N=2  expected=1000", ScheduleCallCenter.schedule(1, 1, 2, PARALLEL_ONLY));

        sep("DATASET 5 — Mixed: fan-out + fan-in + independent");
        //   Setup→Alpha/Beta→Final, Audit runs independently
        //   critical path = 100+600+200 = 900 = Audit(900)  → completion = 900
        print("N=3  expected=900",  ScheduleCallCenter.schedule(2, 2, 3, MIXED));
        print("N=1  expected=2200", ScheduleCallCenter.schedule(2, 2, 1, MIXED));
        print("N=0  expected=900",  ScheduleCallCenter.schedule(2, 2, 0, MIXED));

        sep("DATASET 6 — Base64-encoded input (same as Dataset 1)");
        // The spec provided the sample as Base64 "to avoid email-encoding issues".
        // Base64 is transport encoding, not encryption — decodes to identical literal.
        // Multi-line Base64 is supported (whitespace stripped before decode).
        String base64Sample =
            "SG9tZSxPUiBKZWZmZXJzb24sNTQ0NDtNZWRpY2FyZSxPUiBMYWtlLDEzMDQ7TWVkaWNh" +
            "cmUsV0EgS2luZyw0MzA2MTtMaWZlLE9SIE90aGVyLDEyODA2LE1lZGljYXJlLE9SIExh" +
            "a2U7TGlmZSxXQSBPdGhlciw3MDk0NCxNZWRpY2FyZSxXQSBLaW5nCg==";
        print("N=0  expected=114005,OR Other,WA Other",
              ScheduleCallCenter.schedule(2, 1, 0, base64Sample));
        print("N=2  expected=114005,OR Other,WA King,WA Other",
              ScheduleCallCenter.schedule(3, 3, 2, base64Sample));

        sep("ERROR CASES");
        print("negative N",       ScheduleCallCenter.schedule(1, 1, -1, SAMPLE));
        print("missing prereq",   ScheduleCallCenter.schedule(1, 1, 1, "A,B,100,Ghost,Node"));
        print("cycle A→B→A",      ScheduleCallCenter.schedule(1, 1, 1, "A,G1,100,B,G2;B,G2,200,A,G1"));
        print("empty input",      ScheduleCallCenter.schedule(1, 1, 1, ""));
        print("bad duration",     ScheduleCallCenter.schedule(1, 1, 1, "A,G,notANumber"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    static void print(String label, String result) {
        System.out.printf("  %-30s → %s%n", label, result);
    }

    static void sep(String title) {
        System.out.println("\n══ " + title + " ══");
    }
}
