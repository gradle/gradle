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

import org.gradle.api.internal.Operation;
import org.gradle.api.internal.concurrent.Synchronizer;
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

    /**
     * Initializes the current process so that it can be a well behaving parent.
     * <p>
     * If the process has be already initialized then the method does nothing.
     */
    public static void intitialize() {
        if (initialized) {
            return;
        }
        synchronizer.synchronize(new Operation() {
            public void execute() {
                if (initialized) {
                    return;
                }
                //even if initialization fails we don't want it to re-run
                initialized = true;

                //make sure the the children will be fully detached on windows:
                if (OperatingSystem.current().isWindows()) {
                    new WindowsHandlesManipulator().uninheritStandardStreams();
                }
            }
        });
    }
}
