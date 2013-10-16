/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.Task;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CommandLineOptionReader {

    public Map<CommandLineOption, Method> getCommandLineOptionsWithMethod(Class taskClazz) {
        Map<CommandLineOption, Method> options = new HashMap<CommandLineOption, Method>();
        for (Class<?> type = taskClazz; type != Object.class && type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                CommandLineOption commandLineOption = method.getAnnotation(CommandLineOption.class);
                if (commandLineOption != null) {
                    options.put(commandLineOption, method);
                }
            }
        }
        return options;
    }

    public Set<CommandLineOption> getCommandLineOptions(Class taskClazz) {
        return getCommandLineOptionsWithMethod(taskClazz).keySet();
    }

    public Map<CommandLineOption, Method> getCommandLineOptionsWithMethod(Task task) {
       return getCommandLineOptionsWithMethod(task.getClass());
    }
}
