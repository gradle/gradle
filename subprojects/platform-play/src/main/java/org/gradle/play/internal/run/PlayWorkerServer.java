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

import org.gradle.api.Action;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.ClassLoaderUtils;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.process.internal.worker.WorkerProcessContext;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.URLClassLoader;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class PlayWorkerServer implements Action<WorkerProcessContext>, PlayRunWorkerServerProtocol, Reloader, Serializable {
    private static final Logger LOGGER = Logging.getLogger(PlayWorkerServer.class);

    private final PlayRunSpec runSpec;
    private final VersionedPlayRunAdapter runAdapter;
    private final AtomicBoolean block = new AtomicBoolean();
    private final AtomicBoolean reload = new AtomicBoolean(true);
    private final AtomicReference<Throwable> buildFailure = new AtomicReference<Throwable>();
    private final BlockingQueue<PlayAppLifecycleUpdate> events = new SynchronousQueue<PlayAppLifecycleUpdate>();

    private boolean stopRequested;

    public PlayWorkerServer(PlayRunSpec runSpec, VersionedPlayRunAdapter runAdapter) {
        this.runSpec = runSpec;
        this.runAdapter = runAdapter;
    }

    @Override
    public void execute(WorkerProcessContext context) {
        final PlayRunWorkerClientProtocol clientProtocol = context.getServerConnection().addOutgoing(PlayRunWorkerClientProtocol.class);
        context.getServerConnection().addIncoming(PlayRunWorkerServerProtocol.class, this);
        context.getServerConnection().connect();
        final PlayAppLifecycleUpdate result = start();
        try {
            clientProtocol.update(result);
            while (!stopRequested) {
                PlayAppLifecycleUpdate update = events.take();
                clientProtocol.update(update);
            }
            LOGGER.debug("Play App stopping");
            events.clear();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private PlayAppLifecycleUpdate start() {
        try {
            InetSocketAddress address = startServer();
            return PlayAppLifecycleUpdate.running(address);
        } catch (Exception e) {
            Logging.getLogger(this.getClass()).error("Failed to run Play", e);
            return PlayAppLifecycleUpdate.failed(e);
        }
    }

    private InetSocketAddress startServer() {
        ClassLoaderUtils.disableUrlConnectionCaching();
        final Thread thread = Thread.currentThread();
        final ClassLoader previousContextClassLoader = thread.getContextClassLoader();
        final ClassLoader classLoader = new URLClassLoader(new DefaultClassPath(runSpec.getClasspath()).getAsURLArray(), null);
        thread.setContextClassLoader(classLoader);
        try {
            Object buildDocHandler = runAdapter.getBuildDocHandler(classLoader, runSpec.getClasspath());
            Object buildLink = runAdapter.getBuildLink(classLoader, this, runSpec.getProjectPath(), runSpec.getApplicationJar(), runSpec.getChangingClasspath(), runSpec.getAssetsJar(), runSpec.getAssetsDirs());
            return runAdapter.runDevHttpServer(classLoader, classLoader, buildLink, buildDocHandler, runSpec.getHttpPort());
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            thread.setContextClassLoader(previousContextClassLoader);
        }
    }

    @Override
    public void stop() {
        stopRequested = true;
        try {
            events.put(PlayAppLifecycleUpdate.stopped());
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void upToDate(Throwable throwable) {
        synchronized (block) {
            reload.set(true);
            buildFailure.set(throwable);

            block.set(false);
            block.notifyAll();
            LOGGER.debug("notify upToDate");
        }
    }

    @Override
    public void outOfDate() {
        synchronized (block) {
            reload.set(false);
            buildFailure.set(null);

            block.set(true);
            block.notifyAll();
            LOGGER.debug("notify outOfDate");
        }
    }

    @Override
    public Result requireUpToDate() throws InterruptedException {
        if (!stopRequested) {
            LOGGER.debug("requireUpToDate");
            events.put(PlayAppLifecycleUpdate.reloadRequested());

            synchronized (block) {
                LOGGER.debug("waiting for block to clear {} ", block.get());
                while (block.get()) {
                    block.wait();
                }
                LOGGER.debug("block cleared {} ", block.get());
                boolean changed = reload.compareAndSet(true, false);
                return new Result(changed, buildFailure.get());
            }
        }
        return new Result(false, null);
    }
}
