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
