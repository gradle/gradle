package org.gradle.util.shutdown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class GradleShutdownHook implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(GradleShutdownHook.class);

    public static void register()
    {
        Runtime.getRuntime().addShutdownHook(new Thread(new GradleShutdownHook(), "gradle-shutdown-hook"));
    }

    private GradleShutdownHook() {
    }

    public void run() {
        final List<ShutdownHookAction> shutdownHookActions = new ArrayList<ShutdownHookAction>(ShutdownHookActionRegister.getSHutHookActions());

        if ( shutdownHookActions.isEmpty() ) {
            logger.info("Nothing to do : no shutdhwon actions found in shutdown hook action register.");
        }
        else {
            for ( final ShutdownHookAction shutdownHookAction : shutdownHookActions ) {
                try {
                    shutdownHookAction.execute();
                }
                catch ( Throwable t ) {
                    logger.error("failed to execute a shutdown action ", t);
                }
            }
        }
    }
}
