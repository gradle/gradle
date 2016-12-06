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
import org.gradle.process.internal.ExecHandle;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.JavaExecHandleFactory;
import org.gradle.process.internal.worker.child.ApplicationClassesInSystemClassLoaderWorkerFactory;
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
    private final ApplicationClassesInSystemClassLoaderWorkerFactory workerFactory;
    private final OutputEventListener outputEventListener;
    private final JavaExecHandleBuilder javaCommand;
    private final Set<String> packages = new HashSet<String>();
    private final Set<File> applicationClasspath = new LinkedHashSet<File>();
    private Action<? super WorkerProcessContext> action;
    private LogLevel logLevel = LogLevel.LIFECYCLE;
    private String baseName = "Gradle Worker";
    private File gradleUserHomeDir;
    private int connectTimeoutSeconds;
    private List<URL> implementationClassPath;

    DefaultWorkerProcessBuilder(JavaExecHandleFactory execHandleFactory, MessagingServer server, IdGenerator<?> idGenerator, ApplicationClassesInSystemClassLoaderWorkerFactory workerFactory, OutputEventListener outputEventListener) {
        this.javaCommand = execHandleFactory.newJavaExec();
        this.server = server;
        this.idGenerator = idGenerator;
        this.workerFactory = workerFactory;
        this.outputEventListener = outputEventListener;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public WorkerProcessBuilder setBaseName(String baseName) {
        this.baseName = baseName;
        return this;
    }

    public String getBaseName() {
        return baseName;
    }

    public WorkerProcessBuilder applicationClasspath(Iterable<File> files) {
        GUtil.addToCollection(applicationClasspath, files);
        return this;
    }

    public Set<File> getApplicationClasspath() {
        return applicationClasspath;
    }

    public WorkerProcessBuilder sharedPackages(String... packages) {
        sharedPackages(Arrays.asList(packages));
        return this;
    }

    public WorkerProcessBuilder sharedPackages(Iterable<String> packages) {
        GUtil.addToCollection(this.packages, packages);
        return this;
    }

    public Set<String> getSharedPackages() {
        return packages;
    }

    public WorkerProcessBuilder worker(Action<? super WorkerProcessContext> action) {
        this.action = action;
        return this;
    }

    public Action<? super WorkerProcessContext> getWorker() {
        return action;
    }

    public JavaExecHandleBuilder getJavaCommand() {
        return javaCommand;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

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

    public void setImplementationClasspath(List<URL> implementationClassPath) {
        this.implementationClassPath = implementationClassPath;
    }

    public List<URL> getImplementationClassPath() {
        return implementationClassPath;
    }


    @Override
    public WorkerProcess build() {
        final DefaultWorkerProcess workerProcess = new DefaultWorkerProcess(connectTimeoutSeconds, TimeUnit.SECONDS);
        ConnectionAcceptor acceptor = server.accept(new Action<ObjectConnection>() {
            public void execute(ObjectConnection connection) {
                DefaultWorkerLoggingProtocol defaultWorkerLoggingProtocol = new DefaultWorkerLoggingProtocol(outputEventListener);
                connection.useParameterSerializers(WorkerLoggingSerializer.create());
                connection.addIncoming(WorkerLoggingProtocol.class, defaultWorkerLoggingProtocol);
                workerProcess.onConnect(connection);
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

        workerFactory.prepareJavaCommand(id, displayName, this, implementationClassPath, localAddress, javaCommand);

        javaCommand.args("'" + displayName + "'");
        ExecHandle execHandle = javaCommand.build();

        workerProcess.setExecHandle(execHandle);
        return workerProcess;
    }
}
