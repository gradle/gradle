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
package org.gradle.process.internal.shutdown;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ShutdownHooks {
    private static final Logger LOGGER = Logging.getLogger(ShutdownHooks.class);
    private static final Map<Runnable, Thread> HOOKS = new ConcurrentHashMap<Runnable, Thread>();

    public static void addShutdownHook(Runnable shutdownHook) {
        Thread thread = new Thread(shutdownHook, "gradle-shutdown-hook");
        HOOKS.put(shutdownHook, thread);
        Runtime.getRuntime().addShutdownHook(thread);
    }

    public static void removeShutdownHook(Runnable shutdownHook) {
        try {
            Thread thread = HOOKS.remove(shutdownHook);
            if (thread != null) {
                Runtime.getRuntime().removeShutdownHook(thread);
            }
        } catch (IllegalStateException e) {
            // When shutting down is in progress, invocation of this method throws exception,
            // interrupting other shutdown hooks, so we catch it here.
            //
            // Caused by: java.lang.IllegalStateException: Shutdown in progress
            //        at java.base/java.lang.ApplicationShutdownHooks.remove(ApplicationShutdownHooks.java:82)
            //        at java.base/java.lang.Runtime.removeShutdownHook(Runtime.java:243)
            LOGGER.error("Remove shutdown hook failed", e);
        }
    }
}
