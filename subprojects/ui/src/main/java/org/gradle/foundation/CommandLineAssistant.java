/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.foundation;

import org.gradle.logging.internal.LoggingCommandLineConverter;
import org.gradle.util.internal.ArgumentsSplitter;

import java.util.Iterator;
import java.util.List;

/**
 * Some helpful functions for manipulating command line arguments.
 */
public class CommandLineAssistant {
    private final LoggingCommandLineConverter loggingCommandLineConverter = new LoggingCommandLineConverter();

    public LoggingCommandLineConverter getLoggingCommandLineConverter() {
        return loggingCommandLineConverter;
    }

    /**
     * This breaks up the full command line string into space-delimited and/or quoted command line arguments. This currently does not handle escaping characters such as quotes.
     *
     * @param fullCommandLine the full command line
     * @return a string array of the separate command line arguments.
     */
    public static String[] breakUpCommandLine(String fullCommandLine) {
        List<String> commandLineArguments = ArgumentsSplitter.split(fullCommandLine);
        return commandLineArguments.toArray(new String[commandLineArguments.size()]);
    }

    public boolean hasLogLevelDefined(String[] commandLineArguments) {
        return hasCommandLineOptionsDefined(commandLineArguments, new CommandLineSearch() {
            public boolean contains(String commandLine) {

                return loggingCommandLineConverter.getLogLevel(commandLine) != null;
            }
        });
    }

    public boolean hasShowStacktraceDefined(String[] commandLineArguments) {
        return hasCommandLineOptionsDefined(commandLineArguments, new CommandLineSearch() {
            public boolean contains(String commandLine) {
                return loggingCommandLineConverter.getShowStacktrace(commandLine) != null;
            }
        });
    }

    public interface CommandLineSearch {
        public boolean contains(String commandLine);
    }

    /**
     * This determines if one of the sought options is defined on the command line. We're only looking for options that are prefixed with a single '-'. Note: this IS case-sensitive.
     *
     * @param commandLineOptions the command line options
     * @param commandLineSearch the options we're looking for. This won't have the prefixed dash in them (just "s", "d", etc.).
     * @return true if one of the sought options exists in the
     */
    private boolean hasCommandLineOptionsDefined(String[] commandLineOptions, CommandLineSearch commandLineSearch) {
        for (
                int commandLineOptionsIndex = 0; commandLineOptionsIndex < commandLineOptions.length; commandLineOptionsIndex++) {
            String commandLineOption = commandLineOptions[commandLineOptionsIndex];

            if (commandLineOption != null && commandLineOption.length() > 1 && commandLineOption.charAt(0) == '-') {
                //everything minus the dash must be equivalent to the sought option.
                String remainder = commandLineOption.substring(1);

                if (commandLineSearch.contains(remainder)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * This appends additional command line options to a task name to generate a full command line option.
     *
     * @param task the task to execute
     * @param additionCommandLineOptions the additional options
     * @return a single command line string.
     */
    public static String appendAdditionalCommandLineOptions(TaskView task, String... additionCommandLineOptions) {
        if (additionCommandLineOptions == null || additionCommandLineOptions.length == 0) {
            return task.getFullTaskName();
        }

        StringBuilder builder = new StringBuilder(task.getFullTaskName());
        builder.append(' ');

        appendAdditionalCommandLineOptions(builder, additionCommandLineOptions);

        return builder.toString();
    }

    /*
   combines the tasks into a single command
    */

    public static String combineTasks(List<TaskView> tasks, String... additionCommandLineOptions) {
        if (tasks == null || tasks.isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        Iterator<TaskView> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            TaskView taskView = iterator.next();
            builder.append(taskView.getFullTaskName());
            if (iterator.hasNext()) {
                builder.append(' ');
            }
        }

        appendAdditionalCommandLineOptions(builder, additionCommandLineOptions);

        return builder.toString();
    }

    public static void appendAdditionalCommandLineOptions(StringBuilder builder, String... additionCommandLineOptions) {
        if (additionCommandLineOptions != null) {
            for (int index = 0; index < additionCommandLineOptions.length; index++) {
                String additionCommandLineOption = additionCommandLineOptions[index];
                builder.append(additionCommandLineOption);
                if (index + 1 < additionCommandLineOptions.length) {
                    builder.append(' ');
                }
            }
        }
    }
}
