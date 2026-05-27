import java.util.*;

/**
 * Stateless analytics layer — completely separate from scheduling logic.
 *
 * <p>Responsibility: given pre-aggregated duration totals, return the
 * top-G group names that belong to the top-C categories, sorted alphabetically.
 *
 * <p>Ranking rule (applied to both categories and groups):
 * <ol>
 *   <li>Total duration <b>descending</b></li>
 *   <li>Name <b>ascending</b> (lexicographic tie-break for determinism)</li>
 * </ol>
 */
public class Analytics {

    /**
     * Returns the alphabetically sorted top-G group names within the top-C categories.
     *
     * @param G               number of groups to return (0 → empty list)
     * @param C               number of top categories to consider
     * @param categoryTotals  category → cumulative duration
     * @param groupTotals     group    → cumulative duration
     * @param categoryToGroups category → set of group names belonging to it
     * @return alphabetically sorted list of at most G group names
     */
    public List<String> topGroups(int G,
                                   int C,
                                   Map<String, Long>        categoryTotals,
                                   Map<String, Long>        groupTotals,
                                   Map<String, Set<String>> categoryToGroups) {
        if (G == 0) return Collections.emptyList();

        // Step 1: rank all categories by total duration, keep top C
        List<String> rankedCategories = rankByTotalDescending(categoryTotals);
        List<String> topCategories    = rankedCategories.subList(
                                            0, Math.min(C, rankedCategories.size()));

        // Step 2: collect group names that belong to at least one top-C category
        Set<String> eligibleGroupNames = new LinkedHashSet<>();
        for (String category : topCategories) {
            Set<String> groupsInCategory = categoryToGroups.get(category);
            if (groupsInCategory != null) {
                eligibleGroupNames.addAll(groupsInCategory);
            }
        }

        // Step 3: build a filtered duration map for eligible groups only
        Map<String, Long> eligibleGroupTotals = new HashMap<>();
        for (String groupName : eligibleGroupNames) {
            if (groupTotals.containsKey(groupName)) {
                eligibleGroupTotals.put(groupName, groupTotals.get(groupName));
            }
        }

        // Step 4: rank eligible groups by total duration, keep top G
        List<String> rankedGroups    = rankByTotalDescending(eligibleGroupTotals);
        List<String> topGroupNames   = rankedGroups.subList(
                                           0, Math.min(G, rankedGroups.size()));

        // Step 5: return alphabetically sorted (spec requirement)
        List<String> result = new ArrayList<>(topGroupNames);
        Collections.sort(result);
        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns names sorted by their total duration descending;
     * lexicographic ascending on ties for deterministic output.
     */
    private List<String> rankByTotalDescending(Map<String, Long> totals) {
        List<String> names = new ArrayList<>(totals.keySet());
        names.sort((a, b) -> {
            int cmp = Long.compare(totals.get(b), totals.get(a)); // descending
            return cmp != 0 ? cmp : a.compareTo(b);               // ascending on tie
        });
        return names;
    }
}
