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
import org.gradle.deployment.internal.DeploymentActivity;
import org.gradle.internal.UncheckedException;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;

public class PlayWorkerClient implements PlayRunWorkerClientProtocol {
    private static final Logger LOGGER = Logging.getLogger(PlayWorkerClient.class);

    private final BlockingQueue<PlayAppStart> startEvent = new SynchronousQueue<PlayAppStart>();
    private final BlockingQueue<PlayAppStop> stopEvent = new SynchronousQueue<PlayAppStop>();
    private final DeploymentActivity activity;

    public PlayWorkerClient(DeploymentActivity activity) {
        this.activity = activity;
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
                activity.alive();
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
