/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal.worker;

import org.gradle.api.Action;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.remote.Address;
import org.gradle.internal.remote.ConnectionAcceptor;
import org.gradle.internal.remote.MessagingServer;
import org.gradle.internal.remote.ObjectConnection;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecHandle;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.JavaExecHandleFactory;
import org.gradle.process.internal.health.memory.JvmMemoryStatus;
import org.gradle.process.internal.health.memory.MemoryAmount;
import org.gradle.process.internal.health.memory.MemoryManager;
import org.gradle.process.internal.worker.child.ApplicationClassesInSystemClassLoaderWorkerImplementationFactory;
import org.gradle.process.internal.worker.child.WorkerJvmMemoryInfoProtocol;
import org.gradle.process.internal.worker.child.WorkerLoggingProtocol;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class DefaultWorkerProcessBuilder implements WorkerProcessBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWorkerProcessBuilder.class);
    private final MessagingServer server;
    private final IdGenerator<?> idGenerator;
    private final ApplicationClassesInSystemClassLoaderWorkerImplementationFactory workerImplementationFactory;
    private final OutputEventListener outputEventListener;
    private final JavaExecHandleBuilder javaCommand;
    private final Set<String> packages = new HashSet<String>();
    private final Set<File> applicationClasspath = new LinkedHashSet<File>();
    private final MemoryManager memoryManager;
    private Action<? super WorkerProcessContext> action;
    private LogLevel logLevel = LogLevel.LIFECYCLE;
    private String baseName = "Gradle Worker";
    private File gradleUserHomeDir;
    private int connectTimeoutSeconds;
    private List<URL> implementationClassPath;
    private boolean shouldPublishJvmMemoryInfo;

    DefaultWorkerProcessBuilder(JavaExecHandleFactory execHandleFactory, MessagingServer server, IdGenerator<?> idGenerator, ApplicationClassesInSystemClassLoaderWorkerImplementationFactory workerImplementationFactory, OutputEventListener outputEventListener, MemoryManager memoryManager) {
        this.javaCommand = execHandleFactory.newJavaExec();
        this.server = server;
        this.idGenerator = idGenerator;
        this.workerImplementationFactory = workerImplementationFactory;
        this.outputEventListener = outputEventListener;
        this.memoryManager = memoryManager;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    @Override
    public WorkerProcessBuilder setBaseName(String baseName) {
        this.baseName = baseName;
        return this;
    }

    @Override
    public String getBaseName() {
        return baseName;
    }

    @Override
    public WorkerProcessBuilder applicationClasspath(Iterable<File> files) {
        GUtil.addToCollection(applicationClasspath, files);
        return this;
    }

    @Override
    public Set<File> getApplicationClasspath() {
        return applicationClasspath;
    }

    @Override
    public WorkerProcessBuilder sharedPackages(String... packages) {
        sharedPackages(Arrays.asList(packages));
        return this;
    }

    @Override
    public WorkerProcessBuilder sharedPackages(Iterable<String> packages) {
        GUtil.addToCollection(this.packages, packages);
        return this;
    }

    @Override
    public Set<String> getSharedPackages() {
        return packages;
    }

    public WorkerProcessBuilder worker(Action<? super WorkerProcessContext> action) {
        this.action = action;
        return this;
    }

    @Override
    public Action<? super WorkerProcessContext> getWorker() {
        return action;
    }

    @Override
    public JavaExecHandleBuilder getJavaCommand() {
        return javaCommand;
    }

    @Override
    public LogLevel getLogLevel() {
        return logLevel;
    }

    @Override
    public WorkerProcessBuilder setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
        return this;
    }

    public File getGradleUserHomeDir() {
        return gradleUserHomeDir;
    }

    public void setGradleUserHomeDir(File gradleUserHomeDir) {
        this.gradleUserHomeDir = gradleUserHomeDir;
    }

    @Override
    public void setImplementationClasspath(List<URL> implementationClassPath) {
        this.implementationClassPath = implementationClassPath;
    }

    public List<URL> getImplementationClassPath() {
        return implementationClassPath;
    }

    @Override
    public void enableJvmMemoryInfoPublishing(boolean shouldPublish) {
        this.shouldPublishJvmMemoryInfo = shouldPublish;
    }

    @Override
    public WorkerProcess build() {
        final WorkerJvmMemoryStatus memoryStatus = shouldPublishJvmMemoryInfo ? new WorkerJvmMemoryStatus() : null;
        final DefaultWorkerProcess workerProcess = new DefaultWorkerProcess(connectTimeoutSeconds, TimeUnit.SECONDS, memoryStatus);
        ConnectionAcceptor acceptor = server.accept(new Action<ObjectConnection>() {
            @Override
            public void execute(final ObjectConnection connection) {
                workerProcess.onConnect(connection, new Runnable() {
                    @Override
                    public void run() {
                        DefaultWorkerLoggingProtocol defaultWorkerLoggingProtocol = new DefaultWorkerLoggingProtocol(outputEventListener);
                        connection.useParameterSerializers(WorkerLoggingSerializer.create());
                        connection.addIncoming(WorkerLoggingProtocol.class, defaultWorkerLoggingProtocol);
                        if (shouldPublishJvmMemoryInfo) {
                            connection.useParameterSerializers(WorkerJvmMemoryInfoSerializer.create());
                            connection.addIncoming(WorkerJvmMemoryInfoProtocol.class, memoryStatus);
                        }
                    }
                });
            }
        });
        workerProcess.startAccepting(acceptor);
        Address localAddress = acceptor.getAddress();

        // Build configuration for GradleWorkerMain
        Object id = idGenerator.generateId();
        String displayName = getBaseName() + " " + id;

        LOGGER.debug("Creating {}", displayName);
        LOGGER.debug("Using application classpath {}", applicationClasspath);
        LOGGER.debug("Using implementation classpath {}", implementationClassPath);

        JavaExecHandleBuilder javaCommand = getJavaCommand();
        javaCommand.setDisplayName(displayName);

        workerImplementationFactory.prepareJavaCommand(id, displayName, this, implementationClassPath, localAddress, javaCommand, shouldPublishJvmMemoryInfo);

        javaCommand.args("'" + displayName + "'");
        if (javaCommand.getMaxHeapSize() == null) {
            javaCommand.setMaxHeapSize("512m");
        }
        ExecHandle execHandle = javaCommand.build();

        workerProcess.setExecHandle(execHandle);

        return new MemoryRequestingWorkerProcess(workerProcess, memoryManager, MemoryAmount.parseNotation(javaCommand.getMinHeapSize()));
    }

    private static class MemoryRequestingWorkerProcess implements WorkerProcess {
        private final WorkerProcess delegate;
        private final MemoryManager memoryResourceManager;
        private final long memoryAmount;

        private MemoryRequestingWorkerProcess(WorkerProcess delegate, MemoryManager memoryResourceManager, long memoryAmount) {
            this.delegate = delegate;
            this.memoryResourceManager = memoryResourceManager;
            this.memoryAmount = memoryAmount;
        }

        @Override
        public WorkerProcess start() {
            memoryResourceManager.requestFreeMemory(memoryAmount);
            return delegate.start();
        }

        @Override
        public ObjectConnection getConnection() {
            return delegate.getConnection();
        }

        @Override
        public ExecResult waitForStop() {
            return delegate.waitForStop();
        }

        @Override
        public JvmMemoryStatus getJvmMemoryStatus() {
            return delegate.getJvmMemoryStatus();
        }

        @Override
        public void stopNow() {
            delegate.stopNow();
        }
    }

    private static class WorkerJvmMemoryStatus implements JvmMemoryStatus, WorkerJvmMemoryInfoProtocol {
        private JvmMemoryStatus snapshot;

        public WorkerJvmMemoryStatus() {
            this.snapshot = new JvmMemoryStatus() {
                @Override
                public long getMaxMemory() {
                    throw new IllegalStateException("JVM memory status has not been reported yet.");
                }

                @Override
                public long getCommittedMemory() {
                    throw new IllegalStateException("JVM memory status has not been reported yet.");
                }
            };
        }

        @Override
        public void sendJvmMemoryStatus(JvmMemoryStatus jvmMemoryStatus) {
            this.snapshot = jvmMemoryStatus;
        }

        @Override
        public long getMaxMemory() {
            return snapshot.getMaxMemory();
        }

        @Override
        public long getCommittedMemory() {
            return snapshot.getCommittedMemory();
        }
    }
}
