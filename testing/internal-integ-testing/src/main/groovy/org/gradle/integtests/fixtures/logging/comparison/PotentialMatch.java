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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.junit.ComparisonFailure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PotentialMatch {
    @VisibleForTesting
    static final String HEADER = "Potential Match (actual lines):";
    private static final String BEGIN_MATCH_INDICATOR = "[";
    private static final String END_MATCH_INDICATOR = "]";
    private static final String MISMATCHED_LINE_INDICATOR = "X";

    private final List<String> expectedLines;
    private final List<String> actualLines;
    private final int matchBeginsActualIdx;
    private final List<Boolean> expectedMatches;

    public static boolean isPossibleMatchIndex(List<String> expectedLines, List<String> actualLines, int matchBeginsActualIdx) {
        return matchBeginsActualIdx >= 0 && matchBeginsActualIdx + expectedLines.size() - 1 < actualLines.size();
    }

    public PotentialMatch(List<String> expectedLines, List<String> actualLines, int matchBeginsActualIdx) {
        Preconditions.checkArgument(isPossibleMatchIndex(expectedLines, actualLines, matchBeginsActualIdx), "match would extend beyond actual lines");

        this.expectedLines = expectedLines;
        this.actualLines = actualLines;
        this.matchBeginsActualIdx = matchBeginsActualIdx;
        this.expectedMatches = calcExpectedMatches(expectedLines, actualLines, matchBeginsActualIdx);
    }

    private List<Boolean> calcExpectedMatches(List<String> expectedLines, List<String> actualLines, int matchBeginsActualIdx) {
        List<Boolean> result = new ArrayList<>(expectedLines.size());
        for (int expectedIdx = 0; expectedIdx < expectedLines.size(); expectedIdx++) {
            int actualIdx = matchBeginsActualIdx + expectedIdx;
            String expected = expectedLines.get(expectedIdx);
            String actual = actualLines.get(actualIdx);
            result.add(Objects.equals(expected, actual));
        }
        return result;
    }

    public long getNumMatches() {
        return expectedMatches.stream().filter(Boolean::booleanValue).count();
    }

    public String buildContext(int maxContextLines) {
        int contextStartActualIdx = Math.max(matchBeginsActualIdx - maxContextLines, 0);
        int contextEndActualIdx = Math.min(matchBeginsActualIdx + expectedLines.size() + maxContextLines - 1, actualLines.size() - 1);
        int padding = calcLineNumberPadding(toLineNumber(contextStartActualIdx), toLineNumber(contextEndActualIdx));

        StringBuilder context = new StringBuilder(HEADER).append('\n');
        for (int actualIdx = contextStartActualIdx; actualIdx <= contextEndActualIdx; actualIdx++) {
            int expectedIdx = actualIdx - matchBeginsActualIdx;

            String prefix = buildPrefix(expectedIdx, actualIdx, padding);
            context.append(prefix)
                    .append(actualLines.get(actualIdx))
                    .append('\n');

            if (isMismatch(expectedIdx)) {
                String comparison = buildComparison(expectedLines.get(expectedIdx), actualLines.get(actualIdx), padding);
                context.append(comparison)
                        .append('\n');
            }
        }
        return context.toString();
    }

    private String buildPrefix(int expectedIdx, int actualIdx, int padding) {
        String lineNum = StringUtils.leftPad(String.valueOf(toLineNumber(actualIdx)), padding, ' ');

        StringBuilder result = new StringBuilder().append(' ');
        if (isBeginningOfMatch(actualIdx)) {
            result.append(BEGIN_MATCH_INDICATOR).append(' ');
        } else {
            result.append(StringUtils.repeat(" ", BEGIN_MATCH_INDICATOR.length() + 1));
        }

        if (isMismatch(expectedIdx)) {
            result.append(MISMATCHED_LINE_INDICATOR).append(' ');
        } else {
            result.append(StringUtils.repeat(" ", MISMATCHED_LINE_INDICATOR.length() + 1));
        }

        if (isEndOfMatch(actualIdx)) {
            result.append(END_MATCH_INDICATOR).append(' ');
        } else {
            result.append(StringUtils.repeat(" ", END_MATCH_INDICATOR.length() + 1));
        }

        result.append(StringUtils.leftPad(lineNum, padding, ' ')).append(": ");
        return result.toString();
    }

    private boolean isMismatch(int expectedIdx) {
        return expectedIdx >= 0 && expectedIdx < expectedMatches.size() && !expectedMatches.get(expectedIdx);
    }

    @VisibleForTesting
    static String buildComparison(String expectedLine, String actualLine, int padding) {
        int comparisonPadding = BEGIN_MATCH_INDICATOR.length() + MISMATCHED_LINE_INDICATOR.length() + END_MATCH_INDICATOR.length() + 4 + padding;

        return StringUtils.leftPad(new ComparisonFailure("", expectedLine, actualLine).getMessage(), comparisonPadding, ' ');
    }

    private int calcLineNumberPadding(int startLineNum, int endLineNum) {
        return Math.max(String.valueOf(startLineNum).length(), String.valueOf(endLineNum).length());
    }

    private int toLineNumber(int index) {
        return index + 1;
    }

    private boolean isBeginningOfMatch(int actualIdx) {
        return actualIdx == matchBeginsActualIdx;
    }

    private boolean isEndOfMatch(int actualIdx) {
        return actualIdx == matchBeginsActualIdx + expectedLines.size() - 1;
    }

    @Override
    public int hashCode() {
        return matchBeginsActualIdx;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PotentialMatch that = (PotentialMatch) o;
        return matchBeginsActualIdx == that.matchBeginsActualIdx && expectedLines.equals(that.expectedLines) && actualLines.equals(that.actualLines);
    }
}
