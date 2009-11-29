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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Tom Eyckmans
 */
public class ShutdownHookActionRegister {
    private static final ShutdownHookActionRegister INSTANCE = new ShutdownHookActionRegister();
    private static final Logger LOGGER = LoggerFactory.getLogger(ShutdownHookActionRegister.class);
    private final List<Runnable> shutdownHookActions = new CopyOnWriteArrayList<Runnable>();

    private ShutdownHookActionRegister() {
        Runtime.getRuntime().addShutdownHook(new Thread(new GradleShutdownHook(), "gradle-shutdown-hook"));
    }

    public static void addShutdownHookAction(Runnable shutdownHookAction) {
        INSTANCE.shutdownHookActions.add(shutdownHookAction);
    }

    public static void removeShutdownHookAction(Runnable shutdownHookAction) {
        INSTANCE.shutdownHookActions.remove(shutdownHookAction);
    }

    private class GradleShutdownHook implements Runnable {
        public void run() {
            for (final Runnable shutdownHookAction : shutdownHookActions) {
                try {
                    shutdownHookAction.run();
                } catch (Throwable t) {
                    LOGGER.error("failed to execute a shutdown action ", t);
                }
            }
        }
    }
}
