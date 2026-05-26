//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
import com.resouce.taskmanagement.*;

void main() throws Exception {
    TaskManage manager = new TaskManage();
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // Add tasks with specific timestamps for testing
    manager.addTask("Design DB schema",
            "Create ERD",
            sdf.parse("2024-01-01 09:00:00").getTime());

    manager.addTask("Write unit tests",
            "Cover edge cases",
            sdf.parse("2024-01-02 10:00:00").getTime());

    manager.addTask("Deploy to staging",
            "Use Docker",
            sdf.parse("2024-01-03 11:00:00").getTime());

    manager.addTask("Design UI mockup",
            "Use Figma",
            sdf.parse("2024-01-04 12:00:00").getTime());

    manager.addTask("Write documentation",
            "API docs",
            sdf.parse("2024-01-05 13:00:00").getTime());

    // ── Test 1: Filter by time ────────────────────────────────────────
    long cutoff = sdf.parse("2024-01-03 23:59:59").getTime();
    System.out.println("=== Tasks before 2024-01-03 ===");
    manager.printTasks(manager.getTasksBefore(cutoff));

    // ── Test 2: Filter by keyword ─────────────────────────────────────
    System.out.println("\n=== Tasks containing 'Design' ===");
    manager.printTasks(manager.getTasksByKeyword("Design"));

    // ── Test 3: Filter by time AND keyword ────────────────────────────
    System.out.println("\n=== Tasks before 2024-01-04 containing 'write' ===");
    long cutoff2 = sdf.parse("2024-01-04 23:59:59").getTime();
    manager.printTasks(manager.getTasksBefore(cutoff2, "write"));
    //TIP Press <shortcut actionId="ShowIntentionActions"/> with your caret at the highlighted text
    // to see how IntelliJ IDEA suggests fixing it.
//    IO.println(String.format("Hello and welcome!"));
//
//    for (int i = 1; i <= 5; i++) {
//        //TIP Press <shortcut actionId="Debug"/> to start debugging your code. We have set one <icon src="AllIcons.Debugger.Db_set_breakpoint"/> breakpoint
//        // for you, but you can always add more by pressing <shortcut actionId="ToggleLineBreakpoint"/>.
//        IO.println("i = " + i);
//    }
//    System.out.println(new java.util.Date());
//    TaskManage manager = new TaskManage();
//
//    // Add tasks
//    manager.addTask("Design DB schema", "Create ERD diagram");
//    manager.addTask("Write unit tests", "Cover all edge cases");
//    manager.addTask("Deploy to staging", "Use Docker container");
//
//    // Print all
//    System.out.println("=== All Tasks ===");
//    manager.printAllTasks();
//
//    // Get by ID
//    System.out.println("\n=== Get Task by ID ===");
//    Task found = manager.getAllTaskById("2");
//    System.out.println(found);
}
