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

package org.gradle.execution;

import org.gradle.api.Task;
import org.gradle.api.internal.tasks.CommandLineOption;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.cli.ParsedCommandLineOption;
import org.gradle.util.JavaMethod;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * by Szczepan Faber, created at: 9/5/12
 */
public class CommandLineTaskConfigurer {

    //TODO SF add coverage and refactor existing tests, Use NotationParser

    public Options getConfigurationEntries(CommandLineParser parser, Set<Task> tasks) {
        Map<String, JavaMethod<Object, ?>> options = new HashMap<String, JavaMethod<Object, ?>>();
        Options out = new Options(options);
        if (tasks.size() != 1) {
            return out;
        }

        for (Class<?> type = tasks.iterator().next().getClass(); type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                CommandLineOption commandLineOption = method.getAnnotation(CommandLineOption.class);
                if (commandLineOption != null) {
                    org.gradle.cli.CommandLineOption option = parser.option(commandLineOption.options());
                    option.hasDescription(commandLineOption.description());
                    if (method.getParameterTypes().length > 0 && !hasSingleBooleanParameter(method)) {
                        option.hasArgument();
                    }
                    options.put(commandLineOption.options()[0], JavaMethod.create(Object.class, Object.class, method));
                }
            }
        }

        return out;
    }

    private static boolean hasSingleBooleanParameter(Method method) {
        if (method.getParameterTypes().length != 1) {
            return false;
        }
        Class<?> type = method.getParameterTypes()[0];
        //TODO SF more testing
        return type == Boolean.class || type == Boolean.TYPE;
    }

    public static class Options {

        private final Map<String, JavaMethod<Object, ?>> options;

        public Options(Map<String, JavaMethod<Object, ?>> options) {
            this.options = options;
        }

        public void maybeConfigure(ParsedCommandLine commandLine, Set targets) {
            for (Map.Entry<String, JavaMethod<Object, ?>> entry : options.entrySet()) {
                if (commandLine.hasOption(entry.getKey())) {
                    for (Object target : targets) {
                        ParsedCommandLineOption o = commandLine.option(entry.getKey());
                        if (o.hasValue()) {
                            entry.getValue().invoke(target, o.getValue());
                        } else {
                            entry.getValue().invoke(target, true);
                        }
                    }
                }
            }
        }
    }
}