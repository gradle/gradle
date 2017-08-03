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

import org.gradle.initialization.BuildGateToken;
import org.gradle.internal.UncheckedException;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;

public class PlayWorkerClient implements PlayRunWorkerClientProtocol, BuildGateToken.GateKeeper {

    private final BuildGateToken gateToken;
    private final BlockingQueue<PlayAppStart> startEvent = new SynchronousQueue<PlayAppStart>();
    private final BlockingQueue<PlayAppStop> stopEvent = new SynchronousQueue<PlayAppStop>();

    public PlayWorkerClient(BuildGateToken gateToken) {
        this.gateToken = gateToken;
        gateToken.addGateKeeper(this);
    }

    @Override
    public void update(PlayAppLifecycleUpdate update) {
        try {
            System.out.println("update = " + update);
            if (update instanceof PlayAppStart) {
                startEvent.put((PlayAppStart)update);
            } else if (update instanceof PlayAppStop) {
                stopEvent.put((PlayAppStop)update);
            } else if (update instanceof PlayAppReload) {
                PlayAppReload playAppReload = (PlayAppReload)update;
                // TODO: Remove
                System.out.println(gateToken);
                if (playAppReload.isReloadStart()) {
                    gateToken.open(this);
                } else {
                    gateToken.close(this);
                }
                // TODO: Remove
                System.out.println(gateToken);
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
            System.out.println("waitForStop");
            return stopEvent.take();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            System.out.println("waitForStop - done");
        }
    }
}
