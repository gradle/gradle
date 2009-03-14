package org.gradle.util.exec;

import java.util.Map;

/**
 * @author Tom Eyckmans
 */
public class ProcessBuilderFactory {
    public ProcessBuilder createProcessBuilder(ExecHandle execHandle) {
        final ProcessBuilder processBuilder = new ProcessBuilder(execHandle.getCommand());

        processBuilder.directory(execHandle.getDirectory());
        final Map<String, String> environment = processBuilder.environment();
        environment.clear();
        environment.putAll(execHandle.getEnvironment());

        return processBuilder;
    }
}
