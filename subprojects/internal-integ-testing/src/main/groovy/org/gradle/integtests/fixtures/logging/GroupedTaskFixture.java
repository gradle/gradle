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

    @Deprecated // Use assertOutputContainsLines(String) instead
    public GroupedTaskFixture assertOutputContains(String... text) {
        String output = getOutput();
        for (String s : text) {
            assert output.contains(s);
        }
        return this;
    }

    /**
     * Given a string containing a list of substrings delimited by line terminators, checks
     * if the sequence of lines is present in the actual task output.
     * <p>
     * Will report sequences where some but not all of the lines are present in the exception, if the entire expected input is not present.
     *
     * @param expected the expected output
     */
    public void assertOutputContainsLines(String expected) {
        assertOutputContainsLines(toLines(expected));
    }

    /**
     * Given a collection of lines checks if the sequence of lines is present in the actual task output.
     * <p>
     * Will report sequences where some but not all of the lines are present in the exception, if the entire expected input is not present.
     *
     * @param expectedLines the expected sequence of lines
     */
    public void assertOutputContainsLines(List<String> expectedLines) {
        List<String> actualLines = toLines(getOutput());
        ExhaustiveLinesSearcher searcher = new ExhaustiveLinesSearcher();
        searcher.assertLinesContainedIn(expectedLines, actualLines);
    }

    /**
     * Given a collection of lines checks if the sequence of lines matches the actual task output exactly.
     * <p>
     * Will report which lines are present and which are mismatched in the exception, if the entire expected input is not present.
     *
     * @param expectedLines the expected sequence of lines
     */
    public void assertOutputContainsExactly(List<String> expectedLines) {
        List<String> actualLines = toLines(getOutput());
        ExhaustiveLinesSearcher searcher = new ExhaustiveLinesSearcher();
        searcher.assertSameLines(expectedLines, actualLines);
    }

    private List<String> toLines(String string) {
        return Arrays.asList(string.split("\\R"));
    }
}
