/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.fixtures.logging;

import org.gradle.integtests.fixtures.logging.comparison.ExhaustiveLinesSearcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.gradle.util.internal.CollectionUtils.filter;
import static org.gradle.util.internal.CollectionUtils.join;

/**
 * Collects the output entries produced by a work item such as a task or an artifact transform,
 * and allows assertions to be made about the output.
 *
 * @see GroupedOutputFixture
 */
public abstract class GroupedWorkOutputFixture {

    private final List<String> outputs = new ArrayList<>(1);

    /**
     * Adds an output entry to the group.
     */
    public void addOutput(String output) {
        outputs.add(output);
    }

    /**
     * Returns concatenated non-empty output entries.
     */
    public String getOutput() {
        List<String> nonEmptyOutputs = filter(outputs, string -> !string.equals(""));
        return join("\n", nonEmptyOutputs);
    }

    /**
     * Returns all output entries.
     */
    public List<String> getOutputs() {
        return outputs;
    }

    /**
     * Given some expectedText, assert that the output of this fixture contains it.
     *
     * @param expectedText the expected lines of expectedText
     * @return this fixture
     */
    public GroupedWorkOutputFixture assertOutputContains(String expectedText) {
        return assertOutputContains(ComparisonFailureFormat.LINEWISE, expectedText);
    }

    /**
     * Given a list of lines, assert that the output of this fixture contains each of them and reports failures in the given diff format.
     * <p>
     * It's sometimes easier to see what the difference between the expected and actual output is in another diff format.
     *
     * @param assertionFailureFormat the format to use for the failure message, if lines are not found
     * @param expectedText the expected lines of expectedText
     * @return this fixture
     */
    public GroupedWorkOutputFixture assertOutputContains(ComparisonFailureFormat assertionFailureFormat, String expectedText) {
        switch (assertionFailureFormat) {
            case LINEWISE:
                return assertOutputContainsUsingSearcher(expectedText, ExhaustiveLinesSearcher.useLcsDiff());
            case UNIFIED:
                return assertOutputContainsUsingSearcher(expectedText, ExhaustiveLinesSearcher.useUnifiedDiff());
            default:
                throw new UnsupportedOperationException("Unknown assertion failure format: " + assertionFailureFormat);
        }
    }

    /**
     * Given expected text checks if the sequence of lines is present and uninterrupted in the actual fixture output.
     * <p>
     * Will throw an exception given sequences where some (but not all) of the lines are present in the exception, if
     * the entire expected input is not present.
     *
     * @param expectedText the expected sequence of lines
     * @param searcher      the searcher to use to find the expected sequence of lines in the actual output
     * @return this fixture
     */
    private GroupedWorkOutputFixture assertOutputContainsUsingSearcher(String expectedText, ExhaustiveLinesSearcher searcher) {
        List<String> actualLines = Arrays.asList(getOutput().split("\\R"));
        List<String> expectedLines = Arrays.asList(expectedText.split("\\R"));
        searcher.assertLinesContainedIn(expectedLines, actualLines);
        return this;
    }

    /**
     * Specifies different formats for reporting a failure of {@link #assertOutputContains(String)}.
     */
    public enum ComparisonFailureFormat {
        /**
         * The new format, which should be aide in quickly finding small differences for comparisons spanning multiple lines.
         */
        LINEWISE,

        /**
         * GitHub Patch format.  This is the format used by the GitHub diff viewer.
         */
        UNIFIED
    }

}
