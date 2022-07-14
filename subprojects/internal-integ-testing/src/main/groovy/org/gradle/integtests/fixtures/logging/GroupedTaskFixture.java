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

import static org.gradle.util.internal.CollectionUtils.filter;
import static org.gradle.util.internal.CollectionUtils.join;

public class GroupedTaskFixture {
    private ComparisonFailureFormat assertionFailureFormat = ComparisonFailureFormat.AUTO;

    private final String taskName;

    private String taskOutcome;

    private final List<String> outputs = new ArrayList<String>(1);

    public GroupedTaskFixture(String taskName) {
        this.taskName = taskName;
    }

    public GroupedTaskFixture withAssertionFailureFormat(ComparisonFailureFormat assertionFailureFormat) {
        this.assertionFailureFormat = assertionFailureFormat;
        return this;
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

    private GroupedTaskFixture assertOutputContains(String text) {
        String output = getOutput();
        assert output.contains(text);
        return this;
    }

    public GroupedTaskFixture assertOutputContains2(String... text) {
        switch (assertionFailureFormat) {
            case LEGACY:
                return assertOutputContainsLegacyFormat(text);
            case LINEWISE:
                return assertOutputContainsUsingSearcher(Arrays.asList(text), new ExhaustiveLinesSearcher());
            case UNIFIED:
                return assertOutputContainsUsingSearcher(Arrays.asList(text), new ExhaustiveLinesSearcher().useUnifiedDiff());
            case AUTO:
                if (text.length == 1) {
                    return assertOutputContainsLegacyFormat(text);
                } else {
                    return assertOutputContainsUsingSearcher(Arrays.asList(text), new ExhaustiveLinesSearcher());
                }
            default:
                throw new UnsupportedOperationException("Unknown assertion failure format: " + assertionFailureFormat);
        }
    }

//    public GroupedTaskFixture assertOutputContains(List<String> text) {
//        return assertOutputContains(text.toArray(new String[text.size()]));
//    }

    private GroupedTaskFixture assertOutputContainsLegacyFormat(String... text) {
        String output = getOutput();
        for (String s : text) {
            assert output.contains(s);
        }
        return this;
    }

    /**
     * Given a collection of lines checks if the sequence of lines is present in the actual task output.
     * <p>
     * Will report sequences where some (but not all) of the lines are present in the exception, if the entire expected input is not present.
     *
     * @param expectedLines the expected sequence of lines
     * @param searcher      the searcher to use to find the expected sequence of lines in the actual output
     */
    private GroupedTaskFixture assertOutputContainsUsingSearcher(List<String> expectedLines, ExhaustiveLinesSearcher searcher) {
        List<String> actualLines = toLines(getOutput());
        searcher.assertLinesContainedIn(expectedLines, actualLines);
        return this;
    }

    private List<String> toLines(String string) {
        return Arrays.asList(string.split("\\R"));
    }

    /**
     * Specifies different formats for repoprting a failure of {@link #assertOutputContains(String...)}.
     */
    public enum ComparisonFailureFormat {
        /**
         * The default format.  If single line failure, uses the {@link #LEGACY} format.  If multiple lines, uses the
         * new {#link LINEWISE} format
         */
        AUTO,
        /**
         * The legacy format, which may be difficult to read for comparisons spanning multiple lines.
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
