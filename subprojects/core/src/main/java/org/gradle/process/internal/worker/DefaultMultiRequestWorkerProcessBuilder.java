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
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.ClasspathUtil;
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

class DefaultMultiRequestWorkerProcessBuilder<WORKER> implements MultiRequestWorkerProcessBuilder<WORKER> {
    private static final Method START_METHOD;
    private static final Method STOP_METHOD;
    private final Class<WORKER> workerType;
    private final Class<?> workerImplementation;
    private final DefaultWorkerProcessBuilder workerProcessBuilder;

    static {
        try {
            START_METHOD = WorkerControl.class.getMethod("start");
            STOP_METHOD = WorkerControl.class.getMethod("stop");
        } catch (NoSuchMethodException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public DefaultMultiRequestWorkerProcessBuilder(Class<WORKER> workerType, Class<?> workerImplementation, DefaultWorkerProcessBuilder workerProcessBuilder) {
        this.workerType = workerType;
        this.workerImplementation = workerImplementation;
        this.workerProcessBuilder = workerProcessBuilder;
        workerProcessBuilder.worker(new WorkerAction(workerImplementation));
        workerProcessBuilder.setImplementationClasspath(ClasspathUtil.getClasspath(workerImplementation.getClassLoader()).getAsURLs());
    }

    @Override
    public WorkerProcessSettings applicationClasspath(Iterable<File> files) {
        workerProcessBuilder.applicationClasspath(files);
        return this;
    }

    @Override
    public Set<File> getApplicationClasspath() {
        return workerProcessBuilder.getApplicationClasspath();
    }

    @Override
    public String getBaseName() {
        return workerProcessBuilder.getBaseName();
    }

    @Override
    public JavaExecHandleBuilder getJavaCommand() {
        return workerProcessBuilder.getJavaCommand();
    }

    @Override
    public LogLevel getLogLevel() {
        return workerProcessBuilder.getLogLevel();
    }

    @Override
    public Set<String> getSharedPackages() {
        return workerProcessBuilder.getSharedPackages();
    }

    @Override
    public WorkerProcessSettings setBaseName(String baseName) {
        workerProcessBuilder.setBaseName(baseName);
        return this;
    }

    @Override
    public WorkerProcessSettings setLogLevel(LogLevel logLevel) {
        workerProcessBuilder.setLogLevel(logLevel);
        return this;
    }

    @Override
    public WorkerProcessSettings sharedPackages(Iterable<String> packages) {
        workerProcessBuilder.sharedPackages(packages);
        return this;
    }

    @Override
    public WorkerProcessSettings sharedPackages(String... packages) {
        workerProcessBuilder.sharedPackages(packages);
        return this;
    }

    @Override
    public WORKER build() {
        final WorkerProcess workerProcess = workerProcessBuilder.build();
        return workerType.cast(Proxy.newProxyInstance(workerType.getClassLoader(), new Class[]{workerType}, new InvocationHandler() {
            private Receiver receiver = new Receiver(getBaseName());
            private RequestProtocol requestProtocol;

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.equals(START_METHOD)) {
                    try {
                        workerProcess.start();
                    } catch (Exception e) {
                        throw WorkerProcessException.runFailed(getBaseName(), e);
                    }
                    workerProcess.getConnection().addIncoming(ResponseProtocol.class, receiver);
                    workerProcess.getConnection().useJavaSerializationForParameters(workerImplementation.getClassLoader());
                    requestProtocol = workerProcess.getConnection().addOutgoing(RequestProtocol.class);
                    workerProcess.getConnection().connect();
                    return null;
                }
                if (method.equals(STOP_METHOD)) {
                    if (requestProtocol != null) {
                        requestProtocol.stop();
                    }
                    try {
                        return workerProcess.waitForStop();
                    } finally {
                        requestProtocol = null;
                    }
                }
                requestProtocol.run(method.getName(), method.getParameterTypes(), args);
                boolean hasResult = receiver.awaitNextResult();
                if (!hasResult) {
                    try {
                        // Reached the end of input, worker has crashed or exited
                        requestProtocol = null;
                        workerProcess.waitForStop();
                        // Worker didn't crash
                        throw new IllegalStateException(String.format("No response was received from %s but the worker process has finished.", getBaseName()));
                    } catch (Exception e) {
                        throw WorkerProcessException.runFailed(getBaseName(), e);
                    }
                }
                return receiver.getNextResult();
            }
        }));
    }
}
