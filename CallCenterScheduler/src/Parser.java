import java.util.ArrayList;
import java.util.List;

/**
 * Stateless parser that converts the raw assignment string into structured records.
 *
 * <p>Input format (semicolon-separated tokens):
 * <pre>
 *   "category,group,time[,prereqCategory,prereqGroup]*"
 * </pre>
 *
 * <p>Escape rules — backslash escapes prevent splitting:
 * <ul>
 *   <li>{@code \,} → literal comma</li>
 *   <li>{@code \;} → literal semicolon</li>
 *   <li>{@code \\} → literal backslash</li>
 * </ul>
 */
public class Parser {

    /**
     * Parses the raw assignments string.
     *
     * @param raw semicolon-separated assignment string
     * @return list of records; each record is
     *         {@code [category, group, duration, prereqCat, prereqGrp, ...]}
     * @throws IllegalArgumentException on null/blank input, missing fields,
     *         invalid duration, or mismatched prerequisite pairs
     */
    public List<List<String>> parseAssignments(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("assignments string is empty");
        }

        List<String> tokens = splitUnescaped(raw, ';');
        List<List<String>> records = new ArrayList<>();

        for (String token : tokens) {
            token = token.trim();
            if (token.isEmpty()) continue;

            List<String> fields = splitUnescaped(token, ',');

            validateRecord(fields, token);
            records.add(fields);
        }
        return records;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /** Validates structure and field content of a single parsed record. */
    private void validateRecord(List<String> fields, String rawToken) {
        if (fields.size() < 3) {
            throw new IllegalArgumentException(
                "Record must have at least category, group, time: [" + rawToken + "]");
        }
        if (fields.get(0).trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Category is blank in: [" + rawToken + "]");
        }
        if (fields.get(1).trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Group is blank in: [" + rawToken + "]");
        }

        // Validate duration is a non-negative integer
        try {
            long duration = Long.parseLong(fields.get(2).trim());
            if (duration < 0) {
                throw new IllegalArgumentException(
                    "Duration must be non-negative in: [" + rawToken + "]");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid duration '" + fields.get(2) + "' in: [" + rawToken + "]");
        }

        // Prerequisite entries must come in (category, group) pairs
        int prereqFieldCount = fields.size() - 3;
        if (prereqFieldCount % 2 != 0) {
            throw new IllegalArgumentException(
                "Prerequisites must be category+group pairs in: [" + rawToken + "]");
        }
    }

    /**
     * Splits {@code s} on {@code delimiter} unless immediately preceded by {@code \}.
     * Recognized escapes: {@code \<delimiter>} and {@code \\}.
     * The backslash is consumed and not included in the output.
     */
    private List<String> splitUnescaped(String s, char delimiter) {
        List<String> parts   = new ArrayList<>();
        StringBuilder segment = new StringBuilder();

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                if (next == delimiter || next == '\\') {
                    segment.append(next);
                    i++; // skip the escaped character
                    continue;
                }
            }

            if (c == delimiter) {
                parts.add(segment.toString());
                segment.setLength(0);
            } else {
                segment.append(c);
            }
        }
        parts.add(segment.toString()); // append final segment
        return parts;
    }
}
