package org.gradle.integtests.fixtures.logging;

import org.apache.commons.collections.map.HashedMap;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses rich console output into its pieces for verification in functional tests
 */
public class TaskGroupingFixture {

    /**
     * All tasks will start with > Task, captures everything starting with : and going until a control char
     */
    private final static String TASK_HEADER = "> Task (:[\\w:]*)[^\\n]*\\n";
    private final static String BUILD_STATUS_FOOTER = "\\n[^\\n]*?BUILD SUCCESSFUL";
    private final static String BUILD_FAILED_FOOTER = "\\n[^\\n]*?FAILURE:";
    private final static String END_OF_TASK_OUTPUT = TASK_HEADER + "|" + BUILD_STATUS_FOOTER + "|" + BUILD_FAILED_FOOTER;

    private static String PRECOMPILED_PATTERN;
    static {
        PRECOMPILED_PATTERN = "(?ms)";
        PRECOMPILED_PATTERN += TASK_HEADER;
        // Capture all output, lazily up until two new lines and an END_OF_TASK designation
        PRECOMPILED_PATTERN += "(.*?(?=\\n\\n(?:[^\\n]*?" + END_OF_TASK_OUTPUT + ")))";
        System.err.println(PRECOMPILED_PATTERN);
    }
    private static final Pattern TASK_OUTPUT_PATTERN = Pattern.compile(PRECOMPILED_PATTERN);

    /**
     * Don't need this if we parse all of the output during construction
     */
    private final String output;
    private Map<String, String> taskToOutput;

    public TaskGroupingFixture(String output) {
        this.output = output;
        parse(output);
    }

    private void parse(String output) {
        taskToOutput = new HashedMap();
        Matcher matcher = TASK_OUTPUT_PATTERN.matcher(output);
        while (matcher.find()) {
            String taskName = matcher.group(1);
            String taskOutput = matcher.group(2);
            if(taskToOutput.containsKey(taskName)) {
                String combinedOutput = taskToOutput.get(taskName) + "\n" + taskOutput;
                taskToOutput.put(taskName, combinedOutput);
            } else {
                taskToOutput.put(taskName, taskOutput);
            }
        }
    }

    public Set<String> getTaskNames() {
        return taskToOutput.keySet();
    }

    public String getOutput(String taskName) {
        return taskToOutput.get(taskName);
    }

    public Map<String, String> getOutputs() {
        return taskToOutput;
    }

}
