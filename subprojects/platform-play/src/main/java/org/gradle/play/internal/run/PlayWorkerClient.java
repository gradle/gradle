/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.initialization.BuildGateToken;
import org.gradle.internal.UncheckedException;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;

public class PlayWorkerClient implements PlayRunWorkerClientProtocol {
    private static final Logger LOGGER = Logging.getLogger(PlayWorkerClient.class);
    public static final String GATED_BUILD_SYSPROP = "org.gradle.internal.play.gated";

    private final BuildGateToken.GateKeeper gateKeeper;
    private final BlockingQueue<PlayAppStart> startEvent = new SynchronousQueue<PlayAppStart>();
    private final BlockingQueue<PlayAppStop> stopEvent = new SynchronousQueue<PlayAppStop>();
    private int gateCount;

    public PlayWorkerClient(BuildGateToken buildGate) {
        if (Boolean.getBoolean(GATED_BUILD_SYSPROP)) {
            this.gateKeeper = buildGate.createGateKeeper();
        } else {
            this.gateKeeper = null;
        }
    }

    @Override
    public void update(PlayAppLifecycleUpdate update) {
        try {
            LOGGER.debug("Update from Play App {}", update);
            if (update instanceof PlayAppStart) {
                startEvent.put((PlayAppStart)update);
            } else if (update instanceof PlayAppStop) {
                stopEvent.put((PlayAppStop)update);
            } else if (update instanceof PlayAppReload) {
                if (gateKeeper != null) {
                    PlayAppReload playAppReload = (PlayAppReload) update;
                    if (playAppReload.isReloadStart()) {
                        if (gateCount == 0) {
                            LOGGER.debug("Opening gate - Play App");
                            gateKeeper.open();
                        }
                        gateCount++;
                    } else {
                        gateCount--;
                        if (gateCount == 0) {
                            LOGGER.debug("Closing gate - Play App");
                            gateKeeper.close();
                        }
                    }
                }
            } else {
                throw new IllegalStateException("Unexpected event " + update);
            }
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public PlayAppStart waitForRunning() {
        try {
            return startEvent.take();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public PlayAppStop waitForStop() {
        try {
            return stopEvent.take();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
