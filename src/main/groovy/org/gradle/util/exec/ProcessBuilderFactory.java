package org.gradle.util.exec;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * Creates a {@link java.lang.ProcessBuilder} based on a {@link org.gradle.util.exec.ExecHandle}.
 *
 * @author Tom Eyckmans
 */
public class ProcessBuilderFactory {
    public ProcessBuilder createProcessBuilder(ExecHandle execHandle) {
        final List<String> command = new ArrayList<String>();
        command.add(execHandle.getCommand());
        command.addAll(execHandle.getArguments());
        
        final ProcessBuilder processBuilder = new ProcessBuilder(command);

        processBuilder.directory(execHandle.getDirectory());
        final Map<String, String> environment = processBuilder.environment();
        environment.clear();
        environment.putAll(execHandle.getEnvironment());

        return processBuilder;
    }
}
