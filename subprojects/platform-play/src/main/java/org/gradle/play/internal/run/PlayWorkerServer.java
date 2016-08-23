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
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.ClassLoaderUtils;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.process.internal.worker.WorkerProcessContext;

import java.io.Serializable;
import java.net.URLClassLoader;
import java.util.concurrent.CountDownLatch;

public class PlayWorkerServer implements Action<WorkerProcessContext>, PlayRunWorkerServerProtocol, Serializable {

    private PlayRunSpec runSpec;
    private VersionedPlayRunAdapter runAdapter;

    private volatile CountDownLatch stop;

    public PlayWorkerServer(PlayRunSpec runSpec, VersionedPlayRunAdapter runAdapter) {
        this.runSpec = runSpec;
        this.runAdapter = runAdapter;
    }

    @Override
    public void execute(WorkerProcessContext context) {
        stop = new CountDownLatch(1);
        final PlayRunWorkerClientProtocol clientProtocol = context.getServerConnection().addOutgoing(PlayRunWorkerClientProtocol.class);
        context.getServerConnection().addIncoming(PlayRunWorkerServerProtocol.class, this);
        context.getServerConnection().connect();
        final PlayAppLifecycleUpdate result = startServer();
        try {
            clientProtocol.update(result);
            stop.await();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            clientProtocol.update(PlayAppLifecycleUpdate.stopped());
        }
    }

    private PlayAppLifecycleUpdate startServer() {
        try {
            run();
            return PlayAppLifecycleUpdate.running();
        } catch (Exception e) {
            Logging.getLogger(this.getClass()).error("Failed to run Play", e);
            return PlayAppLifecycleUpdate.failed(e);
        }
    }

    private void run() {
        ClassLoaderUtils.disableUrlConnectionCaching();
        final Thread thread = Thread.currentThread();
        final ClassLoader previousContextClassLoader = thread.getContextClassLoader();
        final ClassLoader classLoader = new URLClassLoader(new DefaultClassPath(runSpec.getClasspath()).getAsURLArray(), null);
        thread.setContextClassLoader(classLoader);
        try {
            Object buildDocHandler = runAdapter.getBuildDocHandler(classLoader, runSpec.getClasspath());
            Object buildLink = runAdapter.getBuildLink(classLoader, runSpec.getProjectPath(), runSpec.getApplicationJar(), runSpec.getChangingClasspath(), runSpec.getAssetsJar(), runSpec.getAssetsDirs());
            runAdapter.runDevHttpServer(classLoader, classLoader, buildLink, buildDocHandler, runSpec.getHttpPort());
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            thread.setContextClassLoader(previousContextClassLoader);
        }
    }

    @Override
    public void stop() {
        stop.countDown();
    }

    @Override
    public void reload() {
        runAdapter.reload();
    }

    @Override
    public void buildError(Throwable throwable) {
        runAdapter.buildError(throwable);
    }

}
