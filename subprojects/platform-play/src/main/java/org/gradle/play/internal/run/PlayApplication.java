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

import org.gradle.api.GradleException;
import org.gradle.deployment.internal.Deployment;
import org.gradle.internal.UncheckedException;
import org.gradle.process.internal.worker.WorkerProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayApplication implements PlayRunWorkerClientProtocol {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayApplication.class);

    private final PlayRunWorkerServerProtocol workerServer;
    private final WorkerProcess process;
    private final AtomicBoolean stopped;

    private final BlockingQueue<PlayAppStart> startEvent = new SynchronousQueue<PlayAppStart>();
    private final BlockingQueue<PlayAppStop> stopEvent = new SynchronousQueue<PlayAppStop>();
    private final Deployment activity;
    private InetSocketAddress playAppAddress;

    public PlayApplication(Deployment activity, PlayRunWorkerServerProtocol workerServer, WorkerProcess process) {
        this.workerServer = workerServer;
        this.process = process;
        this.stopped = new AtomicBoolean(false);
        this.activity = activity;
    }

    public boolean isRunning() {
        return !stopped.get();
    }

    public InetSocketAddress getPlayAppAddress() {
        return playAppAddress;
    }

    public void stop() {
        workerServer.stop();
        waitForEvent(stopEvent);
        process.waitForStop();
        stopped.set(true);
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
                Deployment.Status status = activity.status();
                workerServer.currentStatus(status.hasChanged(), status.getFailure());
            } else {
                throw new IllegalStateException("Unexpected event " + update);
            }
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public void waitForRunning() {
        PlayAppStart playAppStart = waitForEvent(startEvent);
        if (playAppStart.isRunning()) {
            playAppAddress = playAppStart.getAddress();
        } else {
            throw new GradleException("Unable to start Play application.", playAppStart.getException());
        }

    }

    private <T> T waitForEvent(BlockingQueue<T> queue) {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
