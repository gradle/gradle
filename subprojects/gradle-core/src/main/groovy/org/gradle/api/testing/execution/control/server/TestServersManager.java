package org.gradle.api.testing.execution.control.server;

import org.gradle.api.testing.execution.Pipeline;

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

    public void addServer(Pipeline pipeline) {
        if (pipeline == null) throw new IllegalArgumentException("pipeline == null!");

        serversLock.writeLock().lock();
        try {
            final TestControlServer controlServer = controlServerFactory.createTestControlServer(pipeline);
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

    public int addAndStartServer(Pipeline pipeline) {
        addServer(pipeline);

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
