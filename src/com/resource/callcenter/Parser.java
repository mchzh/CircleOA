package com.resource.callcenter;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    public List<List<String>> parseAssignments(
            String input
    ) {
        List<List<String>> parseResult = new ArrayList<>();
        List<String> singleAssignment = splitEscape(input, ';');

        for (String assignment : singleAssignment) {
            assignment = assignment.trim();
            if (assignment.isEmpty()) continue;
            parseResult.add(splitEscape(assignment, ','));
        }
        return parseResult;
    }

    private List<String> splitEscape(String input, char  delimiter) {
        List<String> rets  = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (char c : input.toCharArray()) {
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == delimiter) {
                rets.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        if (!sb.isEmpty()) rets.add(sb.toString()); // last one
        return rets;
    }
}
