/*
 * Copyright 2009 the original author or authors.
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
