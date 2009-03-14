package org.gradle.util.exec;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * Creates a {@link java.lang.ProcessBuilder} based on a {@link org.gradle.util.exec.ExecHandle}.
 *
 * @author Tom Eyckmans
 */
public class ProcessBuilderFactory {
    private static final Logger logger = LoggerFactory.getLogger(ProcessBuilderFactory.class);

    public ProcessBuilder createProcessBuilder(ExecHandle execHandle) {
        final List<String> commandWithArguments = new ArrayList<String>();
        final String command = execHandle.getCommand();
        if (logger.isDebugEnabled()) {
            logger.debug("creating process builder for command {}", command);
        }
        commandWithArguments.add(command);
        final List<String> arguments = execHandle.getArguments();
        if (logger.isDebugEnabled()) {
            int argumentIndex = 0;
            for ( String argument : arguments ) {
                logger.debug("with argument#{} = {}", argumentIndex, argument);
                argumentIndex++;
            }
        }
        commandWithArguments.addAll(arguments);
        
        final ProcessBuilder processBuilder = new ProcessBuilder(commandWithArguments);

        processBuilder.directory(execHandle.getDirectory());
        final Map<String, String> environment = processBuilder.environment();
        environment.clear();
        environment.putAll(execHandle.getEnvironment());

        return processBuilder;
    }
}
