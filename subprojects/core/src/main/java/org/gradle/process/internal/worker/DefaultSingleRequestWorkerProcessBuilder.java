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

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.remote.ObjectConnection;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.worker.request.Receiver;
import org.gradle.process.internal.worker.request.RequestProtocol;
import org.gradle.process.internal.worker.request.ResponseProtocol;
import org.gradle.process.internal.worker.request.WorkerAction;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;

class DefaultSingleRequestWorkerProcessBuilder<PROTOCOL> implements SingleRequestWorkerProcessBuilder<PROTOCOL> {
    private final Class<PROTOCOL> protocolType;
    private final Class<? extends PROTOCOL> workerImplementation;
    private final DefaultWorkerProcessBuilder builder;

    public DefaultSingleRequestWorkerProcessBuilder(Class<PROTOCOL> protocolType, Class<? extends PROTOCOL> workerImplementation, DefaultWorkerProcessBuilder builder) {
        this.protocolType = protocolType;
        this.workerImplementation = workerImplementation;
        this.builder = builder;
        builder.worker(new WorkerAction(workerImplementation));
        builder.setImplementationClasspath(ClasspathUtil.getClasspath(workerImplementation.getClassLoader()).getAsURLs());
    }

    @Override
    public WorkerProcessSettings setBaseName(String baseName) {
        builder.setBaseName(baseName);
        return this;
    }

    @Override
    public String getBaseName() {
        return builder.getBaseName();
    }

    @Override
    public WorkerProcessSettings applicationClasspath(Iterable<File> files) {
        builder.applicationClasspath(files);
        return this;
    }

    @Override
    public Set<File> getApplicationClasspath() {
        return builder.getApplicationClasspath();
    }

    @Override
    public WorkerProcessSettings sharedPackages(String... packages) {
        builder.sharedPackages(packages);
        return this;
    }

    @Override
    public WorkerProcessSettings sharedPackages(Iterable<String> packages) {
        builder.sharedPackages(packages);
        return this;
    }

    @Override
    public Set<String> getSharedPackages() {
        return builder.getSharedPackages();
    }

    @Override
    public JavaExecHandleBuilder getJavaCommand() {
        return builder.getJavaCommand();
    }

    @Override
    public LogLevel getLogLevel() {
        return builder.getLogLevel();
    }

    @Override
    public WorkerProcessSettings setLogLevel(LogLevel logLevel) {
        builder.setLogLevel(logLevel);
        return this;
    }

    @Override
    public PROTOCOL build() {
        return protocolType.cast(Proxy.newProxyInstance(protocolType.getClassLoader(), new Class[]{protocolType}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                Receiver receiver = new Receiver(getBaseName());
                try {
                    WorkerProcess workerProcess = builder.build();
                    workerProcess.start();
                    ObjectConnection connection = workerProcess.getConnection();
                    RequestProtocol requestProtocol = connection.addOutgoing(RequestProtocol.class);
                    connection.addIncoming(ResponseProtocol.class, receiver);
                    connection.useJavaSerializationForParameters(workerImplementation.getClassLoader());
                    connection.connect();
                    requestProtocol.runThenStop(method.getName(), method.getParameterTypes(), args);
                    boolean hasResult = receiver.awaitNextResult();
                    workerProcess.waitForStop();
                    if (!hasResult) {
                        // Reached the end of input, worker has exited without failing
                        throw new IllegalStateException(String.format("No response was received from %s but the worker process has finished.", getBaseName()));
                    }
                } catch (Exception e) {
                    throw WorkerProcessException.runFailed(getBaseName(), e);
                }
                return receiver.getNextResult();
            }
        }));
    }

}
