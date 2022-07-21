/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.specs.Spec;
import org.gradle.integtests.fixtures.logging.comparison.ExhaustiveLinesSearcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.gradle.util.internal.CollectionUtils.filter;
import static org.gradle.util.internal.CollectionUtils.join;

public class GroupedTaskFixture {
    private final String taskName;

    private String taskOutcome;

    private final List<String> outputs = new ArrayList<String>(1);

    public GroupedTaskFixture(String taskName) {
        this.taskName = taskName;
    }

    protected void addOutput(String output) {
        outputs.add(output);
    }

    public void setOutcome(String taskOutcome) {
        if (this.taskOutcome != null) {
            throw new AssertionError(taskName + " task's outcome is set twice!");
        }
        this.taskOutcome = taskOutcome;
    }

    public String getOutcome(){
        return taskOutcome;
    }

    public String getName() {
        return taskName;
    }

    public String getOutput() {
        List<String> nonEmptyOutputs = filter(outputs, new Spec<String>() {
            @Override
            public boolean isSatisfiedBy(String string) {
                return !string.equals("");
            }
        });
        return join("\n", nonEmptyOutputs);
    }

    @Override
    public String toString() {
        return "Output for task: " + taskName + (null != taskOutcome ? " (" + taskOutcome + ")" : "");
    }

    /**
     * Given some lines of text, assert that the output of this fixture contains each of them.
     *
     * This method behaves differently depending on how many lines are the target of the search.  If a single line is provided,
     * this method uses the original comparison logic and failure format.  If multiple lines are provided, this method uses the
     * new {@link ComparisonFailureFormat#LINEWISE} format, which should be easier to read.
     *
     * @param text the expected lines of text
     * @return this fixture
     */
    public GroupedTaskFixture assertOutputContains(String... text) {
        List<String> lines = Arrays.stream(text).collect(Collectors.toList());
        return assertOutputContains(lines, ComparisonFailureFormat.AUTO);
    }

    /**
     * Given some lines of text, assert that the output of this fixture contains each of them.
     *
     * This method behaves differently depending on how many lines are the target of the search.  If a single line is provided,
     * this method uses the original comparison logic and failure format.  If multiple lines are provided, this method uses the
     * new {@link ComparisonFailureFormat#LINEWISE} format, which should be easier to read.
     *
     * @param text the expected lines of text
     * @return this fixture
     */
    public GroupedTaskFixture assertOutputContains(List<String> text) {
        return assertOutputContains(text, ComparisonFailureFormat.AUTO);
    }

    /**
     * Given a list of lines, assert that the output of this fixture contains each of them and reports failures in the given format.
     *
     * @param text the expected lines of text
     * @param assertionFailureFormat the format to use for the failure message, if lines are not found
     * @return this fixture
     */
    public GroupedTaskFixture assertOutputContains(List<String> text, ComparisonFailureFormat assertionFailureFormat) {
        switch (assertionFailureFormat) {
            case LEGACY:
                return assertOutputContainsUsingLegacy(text);
            case LINEWISE:
                return assertOutputContainsUsingSearcher(text, new ExhaustiveLinesSearcher());
            case UNIFIED:
                return assertOutputContainsUsingSearcher(text, new ExhaustiveLinesSearcher().useUnifiedDiff());
            case AUTO:
                if (text.size() == 1) {
                    return assertOutputContainsUsingLegacy(text);
                } else {
                    return assertOutputContainsUsingSearcher(text, new ExhaustiveLinesSearcher());
                }
            default:
                throw new UnsupportedOperationException("Unknown assertion failure format: " + assertionFailureFormat);
        }
    }

    /**
     * Given some lines of text, assert that the fixture output contains each of them using the legacy comparison logic.
     *
     * This method does <strong>NOT</strong> use the order of the lines when searching for them in the output, each line
     * is searched for individually.  If any fail, and exception is thrown reporting that line only.  This matches the previous
     * behavior of the {@link #assertOutputContains(String...)} method.
     *
     * @param text the expected lines
     * @return this fixture
     */
    private GroupedTaskFixture assertOutputContainsUsingLegacy(List<String> text) {
        String output = getOutput();
        for (String s : text) {
            assert output.contains(s);
        }
        return this;
    }

    /**
     * Given a list of lines checks if the sequence of lines is present and uninterrupted in the actual fixture output.
     * <p>
     * Will throw an exception given sequences where some (but not all) of the lines are present in the exception, if
     * the entire expected input is not present.
     *
     * @param expectedLines the expected sequence of lines
     * @param searcher      the searcher to use to find the expected sequence of lines in the actual output
     * @return this fixture
     */
    private GroupedTaskFixture assertOutputContainsUsingSearcher(List<String> expectedLines, ExhaustiveLinesSearcher searcher) {
        List<String> actualLines = Arrays.asList(getOutput().split("\\R"));
        searcher.assertLinesContainedIn(expectedLines, actualLines);
        return this;
    }

    /**
     * Specifies different formats for reporting a failure of {@link #assertOutputContains(String...)}.
     */
    public enum ComparisonFailureFormat {
        /**
         * The default format.  If single line failure, uses the {@link #LEGACY} format.  If multiple lines, uses the
         * new {#link LINEWISE} format
         */
        AUTO,
        /**
         * The legacy format, which may be difficult to read for comparisons spanning multiple lines.  This format also
         * causes each line to be searched for individually, are succeeds if all lines are found.  The order of the lines
         * is not considered.
         */
        LEGACY,
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
