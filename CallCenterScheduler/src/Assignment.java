/**
 * Immutable value object representing a single call-center assignment.
 *
 * <p>The composite key {@code category + KEY_SEPARATOR + group} is unique
 * per the problem spec and is used as the node identifier throughout the
 * graph, indegree, and critical-path maps.
 */
public class Assignment {

    /** Separator used in every composite key across the whole project. */
    public static final String KEY_SEPARATOR = " | ";

    public final String category;
    public final String group;
    public final long   duration;

    // Cached once at construction — key() is called O(V+E) times during scheduling.
    private final String key;

    public Assignment(String category, String group, long duration) {
        this.category = category;
        this.group    = group;
        this.duration = duration;
        this.key      = category + KEY_SEPARATOR + group;
    }

    /** Canonical composite key used in all maps and graph edges. */
    public String key() {
        return key;
    }

    @Override
    public String toString() {
        return key + "(" + duration + ")";
    }
}
