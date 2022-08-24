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
import org.spockframework.runtime.SpockException;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public final class LineSearchFailures {

    public static void insufficientSize(List<String> expected, List<String> actual) {
        throw new InsufficientSizeLineListComparisonFailure(expected, actual);
    }

    public static void potentialMatchesExist(List<String> expected, List<String> actual, Collection<PotentialMatch> potentialMatches, boolean useUnifiedDiff) {
        throw new PotentialMatchesExistComparisonFailure(expected, actual, potentialMatches, useUnifiedDiff);
    }

    public static void noMatchingLines(List<String> expected, List<String> actual) {
        throw new NoMatchingLinesExistComparisonFailure(expected, actual);
    }

    private LineSearchFailures() { /* empty */ }

    /* package */ static abstract class AbstractLineListComparisonFailure extends SpockException {
        protected final List<String> expectedLines;
        protected final List<String> actualLines;

        public AbstractLineListComparisonFailure(List<String> expectedLines, List<String> actualLines) {
            this.expectedLines = expectedLines;
            this.actualLines = actualLines;
        }

        @Override
        public abstract String getMessage();
    }

    public static final class DifferentSizesLineListComparisonFailure extends AbstractLineListComparisonFailure {
        @VisibleForTesting
        /* package */ static final String HEADER_TEMPLATE = "Expected: %d lines, but found: %d lines.";

        public DifferentSizesLineListComparisonFailure(List<String> expectedLines, List<String> actualLines) {
            super(expectedLines, actualLines);
        }

        @Override
        public String getMessage() {
            return String.format(HEADER_TEMPLATE, expectedLines.size(), actualLines.size());
        }
    }

    public static final class InsufficientSizeLineListComparisonFailure extends AbstractLineListComparisonFailure {
        @VisibleForTesting
        /* package */ static final String HEADER_TEMPLATE = "Expected content is too long to be contained in actual content.  Expected: %d lines, found: %d lines.";

        public InsufficientSizeLineListComparisonFailure(List<String> expectedLines, List<String> actualLines) {
            super(expectedLines, actualLines);
        }

        @Override
        public String getMessage() {
            return String.format(HEADER_TEMPLATE, expectedLines.size(), actualLines.size());
        }
    }

    /**
     * This exception may be thrown containing 0 potential matches, if a matching line at the end/beginning of the actual lines would begin a potential match group
     * that would extend beyond the end of the given actual lines.
     */
    public static final class PotentialMatchesExistComparisonFailure extends AbstractLineListComparisonFailure {
        @VisibleForTesting
        /* package */ static final String HEADER = "Lines not found.  Similar sections:";
        private static final int DEFAULT_LEADING_CONTEXT_LINES = 3;
        private final Collection<PotentialMatch> potentialMatches;
        private final int maxLeadingContextLines;
        private final boolean useUnifiedDiff;

        public PotentialMatchesExistComparisonFailure(List<String> expectedLines, List<String> actualLines, Collection<PotentialMatch> potentialMatches, boolean useUnifiedDiff) {
            this(expectedLines, actualLines, potentialMatches, DEFAULT_LEADING_CONTEXT_LINES, useUnifiedDiff);
        }

        public PotentialMatchesExistComparisonFailure(List<String> expectedLines, List<String> actualLines, Collection<PotentialMatch> potentialMatches, int maxLeadingContextLines, boolean useUnifiedDiff) {
            super(expectedLines, actualLines);
            this.potentialMatches = potentialMatches;
            this.maxLeadingContextLines = maxLeadingContextLines;
            this.useUnifiedDiff = useUnifiedDiff;
        }

        @Override
        public String getMessage() {
            if (useUnifiedDiff) {
                return HEADER + "\n\n" + String.join("\n", DiffUtils.generateUnifiedDiff(expectedLines, actualLines, maxLeadingContextLines));
            } else {
                return HEADER + "\n\n" + potentialMatches.stream().map(pm -> pm.buildContext(maxLeadingContextLines)).collect(Collectors.joining("\n"));
            }
        }

        public int getNumPotentialMatches() {
            return potentialMatches.size();
        }
    }

    public static final class NoMatchingLinesExistComparisonFailure extends AbstractLineListComparisonFailure {
        @VisibleForTesting
        /* package */  static final String HEADER = "Not a single matching line was found.";

        public NoMatchingLinesExistComparisonFailure(List<String> expectedLines, List<String> actualLines) {
            super(expectedLines, actualLines);
        }

        @Override
        public String getMessage() {
            return HEADER;
        }
    }
}
