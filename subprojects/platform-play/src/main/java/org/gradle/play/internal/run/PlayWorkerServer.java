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

public class PlayWorkerServer implements Action<WorkerProcessContext>, PlayRunWorkerServerProtocol, ReloadListener, Serializable {
    private static final Logger LOGGER = Logging.getLogger(PlayWorkerServer.class);

    private PlayRunSpec runSpec;
    private VersionedPlayRunAdapter runAdapter;

    private boolean reloadRequested;
    private boolean stopRequested;
    private final BlockingQueue<PlayAppLifecycleUpdate> events = new SynchronousQueue<PlayAppLifecycleUpdate>();

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
            while (true) {
                PlayAppLifecycleUpdate update = events.take();
                clientProtocol.update(update);
                if (update instanceof PlayAppStop) {
                    break;
                }
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
        runAdapter.upToDate(throwable);
    }

    @Override
    public void outOfDate() {
        runAdapter.outOfDate();
    }

    @Override
    public void reloadRequested() {
        if (!stopRequested) {
            LOGGER.debug("reloadRequested {}", reloadRequested);
            try {
                if (!reloadRequested) {
                    reloadRequested = true;
                    events.put(PlayAppLifecycleUpdate.reloadRequested());
                }
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }

    @Override
    public void reloadComplete() {
        if (!stopRequested) {
            try {
                LOGGER.debug("reloadComplete {}", reloadRequested);
                if (reloadRequested) {
                    reloadRequested = false;
                    events.put(PlayAppLifecycleUpdate.reloadCompleted());
                }
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }
}
