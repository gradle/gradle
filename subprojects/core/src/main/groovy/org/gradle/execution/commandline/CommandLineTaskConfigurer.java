/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.execution.commandline;

import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.CommandLineOption;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.cli.ParsedCommandLineOption;
import org.gradle.util.JavaMethod;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * by Szczepan Faber, created at: 9/5/12
 */
public class CommandLineTaskConfigurer {

    public List<String> configureTasks(Collection<Task> tasks, List<String> arguments) {
        assert !tasks.isEmpty();
        if (arguments.isEmpty()) {
            return arguments;
        }
        return configureTasksNow(tasks, arguments);
    }

    private List<String> configureTasksNow(Collection<Task> tasks, List<String> arguments) {
        List<String> remainingArguments = null;
        for (Task task : tasks) {
            Map<String, JavaMethod<Object, ?>> options = new HashMap<String, JavaMethod<Object, ?>>();
            CommandLineParser parser = new CommandLineParser();
            for (Class<?> type = task.getClass(); type != Object.class; type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    CommandLineOption commandLineOption = method.getAnnotation(CommandLineOption.class);
                    if (commandLineOption != null) {
                        String optionName = commandLineOption.options()[0];
                        org.gradle.cli.CommandLineOption option = parser.option(optionName);
                        option.hasDescription(commandLineOption.description());
                        if (method.getParameterTypes().length > 0 && !hasSingleBooleanParameter(method)) {
                            option.hasArgument();
                        }
                        options.put(optionName, JavaMethod.create(Object.class, Object.class, method));
                    }
                }
            }

            ParsedCommandLine parsed;
            try {
                parsed = parser.parse(arguments);
            } catch (CommandLineArgumentException e) {
                //we expect that all options must be applicable for each task
                throw new GradleException("Problem configuring task " + task.getPath() + " from command line. " + e.getMessage(), e);
            }
            for (Map.Entry<String, JavaMethod<Object, ?>> entry : options.entrySet()) {
                if (parsed.hasOption(entry.getKey())) {
                    ParsedCommandLineOption o = parsed.option(entry.getKey());
                    if (o.hasValue()) {
                        entry.getValue().invoke(task, o.getValue());
                    } else {
                        entry.getValue().invoke(task, true);
                    }
                }
            }
            //since
            assert remainingArguments == null || remainingArguments.equals(parsed.getExtraArguments())
                : "we expect all options to be consumed by each task so remainingArguments should be the same for each task";
            remainingArguments = parsed.getExtraArguments();
        }
        return remainingArguments;
    }

    private boolean hasSingleBooleanParameter(Method method) {
        if (method.getParameterTypes().length != 1) {
            return false;
        }
        Class<?> type = method.getParameterTypes()[0];
        return type == Boolean.class || type == Boolean.TYPE;
    }
}