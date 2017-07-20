/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.play.internal.run;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.Serializable;

class PendingChanges implements Serializable {
    private static final Logger LOGGER = Logging.getLogger(PendingChanges.class);

    private int pendingChanges;

    synchronized void increment() {
        pendingChanges++;
        LOGGER.debug("increment() {}", pendingChanges);
    }

    synchronized void decrement() {
        // Clamp to 0
        pendingChanges = Math.max(0, pendingChanges-1);
        LOGGER.debug("decrement() {}", pendingChanges);
        if (pendingChanges == 0) {
            LOGGER.debug("decrement() notifyAll {}", pendingChanges);
            notifyAll();
        }
    }

    synchronized void waitForAll() throws InterruptedException {
        LOGGER.debug("waitForAll() wait {}", pendingChanges);
        while (pendingChanges > 0) {
            wait();
        }
        LOGGER.debug("waitForAll() decrement {}", pendingChanges);
    }
}
