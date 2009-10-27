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
package org.gradle.api.testing.execution.control.server;

import org.gradle.api.testing.execution.Pipeline;
import org.gradle.api.testing.execution.PipelineDispatcher;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Tom Eyckmans
 */
public class TestServersManager {
    private final ReadWriteLock serversLock;
    private final Map<Pipeline, TestControlServer> pipelineServers;

    private final ControlServerFactory controlServerFactory;

    public TestServersManager(ControlServerFactory controlServerFactory) {
        if (controlServerFactory == null) throw new IllegalArgumentException("controlServerFactory == null!");

        this.controlServerFactory = controlServerFactory;

        serversLock = new ReentrantReadWriteLock();
        pipelineServers = new HashMap<Pipeline, TestControlServer>();
    }

    private TestControlServer getServer(Pipeline pipeline) {
        if (pipeline == null) throw new IllegalArgumentException("pipeline == null!");

        serversLock.readLock().lock();
        try {
            return pipelineServers.get(pipeline);
        }
        finally {
            serversLock.readLock().unlock();
        }
    }

    public void addServer(Pipeline pipeline, PipelineDispatcher pipelineDispatcher) {
        if (pipeline == null) throw new IllegalArgumentException("pipeline == null!");

        serversLock.writeLock().lock();
        try {
            final TestControlServer controlServer = controlServerFactory.createTestControlServer(pipeline, pipelineDispatcher);
            pipelineServers.put(pipeline, controlServer);
        }
        finally {
            serversLock.writeLock().unlock();
        }
    }

    public int startServer(Pipeline pipeline) {
        if (pipeline == null) throw new IllegalArgumentException("pipeline == null!");

        final TestControlServer controlServer = getServer(pipeline);

        if (controlServer == null) throw new IllegalStateException("no server found for pipeline " + pipeline.getId());

        return controlServer.start();
    }

    public int addAndStartServer(Pipeline pipeline, PipelineDispatcher pipelineDispatcher) {
        addServer(pipeline, pipelineDispatcher);

        return startServer(pipeline);
    }

    public int getServerPort(Pipeline pipeline) {
        if (pipeline == null) throw new IllegalArgumentException("pipeline == null!");

        final TestControlServer controlServer = getServer(pipeline);

        if (controlServer == null) throw new IllegalStateException("no server found for pipeline " + pipeline.getId());

        return controlServer.getPort();
    }

    public void stopServer(Pipeline pipeline) {
        if (pipeline == null) throw new IllegalArgumentException("pipeline == null!");

        final TestControlServer controlServer = getServer(pipeline);

        if (controlServer == null) throw new IllegalStateException("no server found for pipeline " + pipeline.getId());

        controlServer.stop();
    }
}
