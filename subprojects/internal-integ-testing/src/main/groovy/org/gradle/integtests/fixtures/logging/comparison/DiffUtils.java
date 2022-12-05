/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.integtests.fixtures.logging.comparison;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Copied from the Google Truth project: https://github.com/google/truth/blob/master/core/src/main/java/com/google/common/truth/DiffUtils.java.
 *
 * A custom implementation of the diff algorithm based on the solution described at
 * https://en.wikipedia.org/wiki/Longest_common_subsequence_problem
 *
 * Original author Yun Peng (pcloudy@google.com)
 */
public final class DiffUtils {
    // A list of unique strings appeared in compared texts.
    // The index of each string is its incremental Id.
    private final List<String> stringList = new ArrayList<>();
    // A map to record each unique string and its incremental id.
    private final Map<String, Integer> stringToId = new HashMap<>();
    private int[] original;
    private int[] revised;
    // lcs[i][j] is the length of the longest common sequence of original[1..i] and revised[1..j].
    private int[][] lcs;
    private final List<Character> unifiedDiffType = new ArrayList<>();
    private final List<Integer> unifiedDiffContentId = new ArrayList<>();
    private final List<String> reducedUnifiedDiff = new ArrayList<>();
    private int offsetHead = 0;
    private int offsetTail = 0;

    private List<String> diff(
            List<String> originalLines, List<String> revisedLines, int contextSize) {
        reduceEqualLinesFromHeadAndTail(originalLines, revisedLines, contextSize);
        originalLines = originalLines.subList(offsetHead, originalLines.size() - offsetTail);
        revisedLines = revisedLines.subList(offsetHead, revisedLines.size() - offsetTail);

        original = new int[originalLines.size() + 1];
        revised = new int[revisedLines.size() + 1];
        lcs = new int[originalLines.size() + 1][revisedLines.size() + 1];

        for (int i = 0; i < originalLines.size(); i++) {
            original[i + 1] = getIdByLine(originalLines.get(i));
        }
        for (int i = 0; i < revisedLines.size(); i++) {
            revised[i + 1] = getIdByLine(revisedLines.get(i));
        }

        for (int i = 1; i < original.length; i++) {
            for (int j = 1; j < revised.length; j++) {
                if (original[i] == revised[j]) {
                    lcs[i][j] = lcs[i - 1][j - 1] + 1;
                } else {
                    lcs[i][j] = max(lcs[i][j - 1], lcs[i - 1][j]);
                }
            }
        }

        calcUnifiedDiff(originalLines.size(), revisedLines.size());

        calcReducedUnifiedDiff(contextSize);

        return reducedUnifiedDiff;
    }

    /** Calculate an incremental Id for a given string. */
    private Integer getIdByLine(String line) {
        int newId = stringList.size();
        Integer existingId = stringToId.put(line, newId);
        if (existingId == null) {
            stringList.add(line);
            return newId;
        } else {
            stringToId.put(line, existingId);
            return existingId;
        }
    }

    /** An optimization to reduce the problem size by removing equal lines from head and tail. */
    private void reduceEqualLinesFromHeadAndTail(
            List<String> original, List<String> revised, int contextSize) {
        int head = 0;
        int maxHead = min(original.size(), revised.size());
        while (head < maxHead && original.get(head).equals(revised.get(head))) {
            head++;
        }
        head = max(head - contextSize, 0);
        offsetHead = head;

        int tail = 0;
        int maxTail = min(original.size() - head - contextSize, revised.size() - head - contextSize);
        while (tail < maxTail
                && original
                .get(original.size() - 1 - tail)
                .equals(revised.get(revised.size() - 1 - tail))) {
            tail++;
        }
        tail = max(tail - contextSize, 0);
        offsetTail = tail;
    }

    private void calcUnifiedDiff(int i, int j) {
        while (i > 0 || j > 0) {
            if (i > 0
                    && j > 0
                    && original[i] == revised[j]
                    // Make sure the diff output is identical to the diff command line tool when there are
                    // multiple solutions.
                    && lcs[i - 1][j - 1] + 1 > lcs[i - 1][j]
                    && lcs[i - 1][j - 1] + 1 > lcs[i][j - 1]) {
                unifiedDiffType.add(' ');
                unifiedDiffContentId.add(original[i]);
                i--;
                j--;
            } else if (j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j])) {
                unifiedDiffType.add('+');
                unifiedDiffContentId.add(revised[j]);
                j--;
            } else if (i > 0 && (j == 0 || lcs[i][j - 1] < lcs[i - 1][j])) {
                unifiedDiffType.add('-');
                unifiedDiffContentId.add(original[i]);
                i--;
            }
        }
        Collections.reverse(unifiedDiffType);
        Collections.reverse(unifiedDiffContentId);
    }

    /**
     * Generate the unified diff with a given context size
     *
     * @param contextSize The context size we should leave at the beginning and end of each block.
     */
    private void calcReducedUnifiedDiff(int contextSize) {
        // The index of the next line we're going to process in fullDiff.
        int next = 0;
        // The number of lines in original/revised file after the diff lines we've processed.
        int lineNumOrigin = offsetHead;
        int lineNumRevised = offsetHead;
        while (next < unifiedDiffType.size()) {
            // The start and end index of the current block in fullDiff
            int start;
            int end;
            // The start line number of the block in original/revised file.
            int startLineOrigin;
            int startLineRevised;
            // Find the next diff line that is not an equal line.
            while (next < unifiedDiffType.size() && unifiedDiffType.get(next).equals(' ')) {
                next++;
                lineNumOrigin++;
                lineNumRevised++;
            }
            if (next == unifiedDiffType.size()) {
                break;
            }
            // Calculate the start line index of the current block in fullDiff
            start = max(0, next - contextSize);

            // Record the start line number in original and revised file of the current block
            startLineOrigin = lineNumOrigin - (next - start - 1);
            startLineRevised = lineNumRevised - (next - start - 1);

            // The number of consecutive equal lines in fullDiff, we must find at least
            // contextSize * 2 + 1 equal lines to identify the end of the block.
            int equalLines = 0;
            // Let `end` points to the last non-equal diff line
            end = next;
            while (next < unifiedDiffType.size() && equalLines < contextSize * 2 + 1) {
                if (unifiedDiffType.get(next).equals(' ')) {
                    equalLines++;
                    lineNumOrigin++;
                    lineNumRevised++;
                } else {
                    equalLines = 0;
                    // Record the latest non-equal diff line
                    end = next;
                    if (unifiedDiffType.get(next).equals('-')) {
                        lineNumOrigin++;
                    } else {
                        // line starts with "+"
                        lineNumRevised++;
                    }
                }
                next++;
            }
            // Calculate the end line index of the current block in fullDiff
            end = min(end + contextSize + 1, unifiedDiffType.size());

            // Calculate the size of the block content in original/revised file
            int blockSizeOrigin = lineNumOrigin - startLineOrigin - (next - end - 1);
            int blockSizeRevised = lineNumRevised - startLineRevised - (next - end - 1);

            StringBuilder header = new StringBuilder();
            header
                    .append("@@ -")
                    .append(startLineOrigin)
                    .append(",")
                    .append(blockSizeOrigin)
                    .append(" +")
                    .append(startLineRevised)
                    .append(",")
                    .append(blockSizeRevised)
                    .append(" @@");

            reducedUnifiedDiff.add(header.toString());
            for (int i = start; i < end; i++) {
                reducedUnifiedDiff.add(
                        unifiedDiffType.get(i) + stringList.get(unifiedDiffContentId.get(i)));
            }
        }
    }

    public static List<String> generateUnifiedDiff(
            List<String> original, List<String> revised, int contextSize) {
        return new DiffUtils().diff(original, revised, contextSize);
    }
}
