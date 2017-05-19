package org.gradle.integtests.fixtures.logging;

import org.apache.commons.collections.map.HashedMap;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses rich console output into its pieces for verification in functional tests
 * TODO: Rename to GroupedOutputFixture
 */
public class TaskGroupingFixture {

    /**
     * All tasks will start with > Task, captures everything starting with : and going until a control char
     */
    private final static String TASK_HEADER = "> Task (:[\\w:]*)[^\\n]*\\n";

    private final static String BUILD_STATUS_FOOTER = "\\n[^\\n]*?BUILD SUCCESSFUL";
    private final static String BUILD_FAILED_FOOTER = "\\n[^\\n]*?FAILURE:";
    private final static String PROGRESS_BAR = "\u001B\\[0K\\n\u001B\\[1A \\[1m<";
    private final static String END_OF_TASK_OUTPUT = TASK_HEADER + "|" + BUILD_STATUS_FOOTER + "|" + BUILD_FAILED_FOOTER + "|" + PROGRESS_BAR;

    private static String PRECOMPILED_PATTERN;
    private static final Pattern TASK_OUTPUT_PATTERN;

    static {
        PRECOMPILED_PATTERN = "(?ms)";
        PRECOMPILED_PATTERN += TASK_HEADER;
        // Capture all output, lazily up until two new lines and an END_OF_TASK designation
        PRECOMPILED_PATTERN += "(.*?(?=\\n\\n(?:[^\\n]*?" + END_OF_TASK_OUTPUT + ")))";
        TASK_OUTPUT_PATTERN = Pattern.compile(PRECOMPILED_PATTERN);
        //TODO: Remove once stable
        System.err.println(PRECOMPILED_PATTERN);
    }

    /**
     * Don't need this if we parse all of the output during construction
     */
    private final String output;
    private Map<String, GroupedTaskFixture> tasks;

    public TaskGroupingFixture(String output) {
        this.output = output;
        parse(output);
    }

    private void parse(String output) {
        tasks = new HashedMap();
        Matcher matcher = TASK_OUTPUT_PATTERN.matcher(output);
        while (matcher.find()) {
            String taskName = matcher.group(1);
            String taskOutput = matcher.group(2);
            if (tasks.containsKey(taskName)) {
                tasks.get(taskName).addOutput(taskOutput);
            } else {
                GroupedTaskFixture task = new GroupedTaskFixture(taskName);
                task.addOutput(taskOutput);
                tasks.put(taskName, task);
            }
        }
    }

    public int getTaskCount() {
        return tasks.size();
    }

    public GroupedTaskFixture task(String taskName) {
        if (tasks.containsKey(taskName)) {
            return tasks.get(taskName);
        }
        return new GroupedTaskFixture(taskName);
    }
}
