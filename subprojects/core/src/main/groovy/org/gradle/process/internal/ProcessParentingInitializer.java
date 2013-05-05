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

package org.gradle.process.internal;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.Synchronizer;
import org.gradle.internal.nativeplatform.jna.WindowsHandlesManipulator;
import org.gradle.internal.os.OperatingSystem;

/**
 * Initializes for a well behaved parent process.
 * <p>
 * by Szczepan Faber, created at: 3/2/12
 */
public class ProcessParentingInitializer {

    private static boolean initialized;
    private static Synchronizer synchronizer = new Synchronizer();
    private static final Logger LOGGER = Logging.getLogger(ProcessParentingInitializer.class);

    /**
     * Initializes the current process so that it can be a well behaving parent.
     * <p>
     * If the process has been already initialized then the method does nothing.
     */
    public static void intitialize() {
        ProcessParentingInitializer.intitialize(new Factory<Object>() {
            public Object create() { return null; } //no op
        });
    }
    
    /**
     * Initializes the current process so that it can be a well behaving parent.
     * <p>
     * Intended to solve an intermittent windows problem that made some child processes not quite detached in concurrent scenario.
     * This method is useful when the child process is intended to live longer than the parent process (this process)
     * *and* you would like to support thread safety. Use it if spawning the child processes may occur concurrently.
     * <p>
     * The operation parameter is intended to spawn the well behaving daemon process that closes its inputs/outputs.
     * The operation should not wait for the daemon to finish - it should rather monitor the daemon for a little bit
     * to make sure it started properly and then return or throw. For example, the operation can consume daemon outputs
     * until the daemon closes them.
     * <p>
     * operation parameter is a Factory so that your spawning operation can return value should you need it.
     * <p>
     * If the parent process has been already initialized then the method simply executes the operation.
     */
    public static <T> T intitialize(final Factory<T> operation) {
        if (initialized) {
            return operation.create();
        }
        return synchronizer.synchronize(new Factory<T>() {
            public T create() {
                if (initialized) {
                    return operation.create();
                }
                try {
                    //make sure the the children will be fully detached on windows:
                    if (OperatingSystem.current().isWindows()) {
                        new WindowsHandlesManipulator().uninheritStandardStreams();
                    }
                    return operation.create();
                } finally {
                    //even if initialization fails we don't want it to re-run
                    initialized = true;
                    LOGGER.info("An attempt to initialize for well behaving parent process finished.");
                }
            }
        });
    }
}