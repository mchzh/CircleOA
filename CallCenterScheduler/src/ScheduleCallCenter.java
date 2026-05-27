import java.util.*;
import java.util.Base64;

/**
 * Public API — thin orchestration layer.
 *
 * <p>Wires together {@link Parser}, {@link Scheduler}, and {@link Analytics}
 * and formats the final output string. All error handling is centralised here
 * so callers always receive either a valid result or {@code "ERROR <reason>"}.
 *
 * <p><b>Input encoding:</b> The {@code assignments} parameter is accepted in two forms:
 * <ul>
 *   <li><b>Literal</b> — semicolon-separated string as defined by the spec.</li>
 *   <li><b>Base64</b> — the same literal string Base64-encoded (e.g. for email transport).
 *       Base64 is a transport encoding, not encryption; the payload is identical once decoded.</li>
 * </ul>
 * Auto-detection: if the string contains no semicolons or commas and matches the
 * Base64 character set, it is decoded before parsing.
 */
public class ScheduleCallCenter {

    private static final Parser    parser    = new Parser();
    private static final Scheduler scheduler = new Scheduler();
    private static final Analytics analytics = new Analytics();

    /**
     * Schedules assignments and returns a formatted result string.
     *
     * @param G           top-G groups to include (0 = completion time only)
     * @param C           top-C categories used to filter groups
     * @param N           number of concurrent workers (0 = unlimited)
     * @param assignments literal or Base64-encoded semicolon-separated assignment string
     * @return            {@code "completionTime[,group1,group2,...]"}
     *                    or {@code "ERROR <reason>"} on any failure
     */
    public static String schedule(int G, int C, int N, String assignments) {
        try {
            validateParameters(G, C, N);

            // Auto-detect and decode Base64 input before parsing
            String literalInput = decodeIfBase64(assignments);

            List<List<String>>  records        = parser.parseAssignments(literalInput);
            Scheduler.Result    scheduleResult = scheduler.schedule(N, records);
            StringJoiner        resultJoiner   = new StringJoiner(",");

            resultJoiner.add(String.valueOf(scheduleResult.completionTime));

            if (G > 0) {
                List<String> topGroupNames = analytics.topGroups(
                    G, C,
                    scheduleResult.categoryTotals,
                    scheduleResult.groupTotals,
                    scheduleResult.categoryToGroups
                );
                for (String groupName : topGroupNames) {
                    resultJoiner.add(groupName);
                }
            }

            return resultJoiner.toString();

        } catch (IllegalArgumentException e) {
            return "ERROR " + e.getMessage();
        } catch (Exception e) {
            return "ERROR Unexpected: " + e.getMessage();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Detects whether {@code input} is Base64-encoded and, if so, decodes it.
     *
     * <p>Detection heuristic: a literal assignment string always contains
     * at least one comma {@code ','} or semicolon {@code ';'}. A pure Base64
     * string contains only {@code [A-Za-z0-9+/=]} and whitespace.
     * If no comma or semicolon is found and the string is valid Base64,
     * we decode it.
     *
     * <p>Note: Base64 is <em>not</em> encryption — it is a reversible
     * transport encoding. The decoded bytes are identical to the literal input.
     *
     * @param input raw string from the caller
     * @return the literal assignment string (decoded if Base64, unchanged otherwise)
     */
    private static String decodeIfBase64(String input) {
        if (input == null) return input;

        String stripped = input.trim();

        // Literal strings always contain ',' or ';' — if absent, suspect Base64
        boolean hasLiteralDelimiter = stripped.indexOf(',') >= 0
                                   || stripped.indexOf(';') >= 0;
        if (hasLiteralDelimiter) {
            return stripped; // already a literal string
        }

        // Attempt Base64 decode — if it fails the input is malformed anyway
        try {
            // Remove all whitespace (Base64 may be line-wrapped)
            String compacted = stripped.replaceAll("\\s+", "");
            byte[] decoded   = Base64.getDecoder().decode(compacted);
            String literal   = new String(decoded, java.nio.charset.StandardCharsets.UTF_8).trim();
            return literal;
        } catch (IllegalArgumentException e) {
            // Not valid Base64 — return as-is and let the parser report the error
            return stripped;
        }
    }

    /** Validates G, C, N are all non-negative. */
    private static void validateParameters(int G, int C, int N) {
        if (G < 0) throw new IllegalArgumentException("G must be non-negative");
        if (C < 0) throw new IllegalArgumentException("C must be non-negative");
        if (N < 0) throw new IllegalArgumentException("N must be non-negative");
    }
}
