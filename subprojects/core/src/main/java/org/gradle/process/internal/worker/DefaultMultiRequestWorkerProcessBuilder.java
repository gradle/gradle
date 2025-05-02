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
import org.gradle.internal.Cast;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.serialize.Serializer;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.worker.request.Receiver;
import org.gradle.process.internal.worker.request.Request;
import org.gradle.process.internal.worker.request.RequestArgumentSerializers;
import org.gradle.process.internal.worker.request.RequestProtocol;
import org.gradle.process.internal.worker.request.RequestSerializerRegistry;
import org.gradle.process.internal.worker.request.ResponseProtocol;
import org.gradle.process.internal.worker.request.WorkerAction;

import java.io.File;
import java.util.Collections;
import java.util.Set;

class DefaultMultiRequestWorkerProcessBuilder<IN, OUT> implements MultiRequestWorkerProcessBuilder<IN, OUT> {

    private final Class<?> workerImplementation;
    private final OutputEventListener outputEventListener;

    // Mutable state
    private final DefaultWorkerProcessBuilder workerProcessBuilder;
    private final RequestArgumentSerializers argumentSerializers = new RequestArgumentSerializers();
    private boolean useApplicationClassloaderOnly;

    public DefaultMultiRequestWorkerProcessBuilder(Class<?> workerImplementation, DefaultWorkerProcessBuilder workerProcessBuilder, OutputEventListener outputEventListener) {
        this.workerImplementation = workerImplementation;
        this.workerProcessBuilder = workerProcessBuilder;
        this.outputEventListener = outputEventListener;
    }

    @Override
    public WorkerProcessSettings applicationClasspath(Iterable<File> files) {
        workerProcessBuilder.applicationClasspath(files);
        return this;
    }

    @Override
    public WorkerProcessSettings applicationModulePath(Iterable<File> files) {
        workerProcessBuilder.applicationModulePath(files);
        return this;
    }

    @Override
    public Set<File> getApplicationModulePath() {
        return workerProcessBuilder.getApplicationModulePath();
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
    public <T> void registerArgumentSerializer(Class<T> type, Serializer<T> serializer) {
        argumentSerializers.register(type, serializer);
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
    public void withoutAutomaticImplementationClasspath() {
        this.useApplicationClassloaderOnly = true;
    }

    @Override
    public MultiRequestClient<IN, OUT> build() {
        workerProcessBuilder.worker(new WorkerAction(this.workerImplementation));

        if (useApplicationClassloaderOnly) {
            workerProcessBuilder.setImplementationClasspath(Collections.emptyList());
        } else {
            ClassPath implementationClasspath = ClasspathUtil.getClasspath(this.workerImplementation.getClassLoader());
            workerProcessBuilder.setImplementationClasspath(implementationClasspath.getAsURLs());
        }

        // Always publish process info for multi-request workers
        workerProcessBuilder.enableJvmMemoryInfoPublishing(true);
        final WorkerProcess workerProcess = workerProcessBuilder.build();

        return new MultiRequestClient<IN, OUT>() {
            private Receiver receiver = new Receiver(getBaseName(), outputEventListener);
            private RequestProtocol requestProtocol;

            @Override
            public WorkerProcess start() {
                // Note -- leaks current build operation to worker thread, it will be cleared after the worker is started
                try {
                    workerProcess.start();
                } catch (Exception e) {
                    throw WorkerProcessException.runFailed(getBaseName(), e);
                }
                workerProcess.getConnection().addIncoming(ResponseProtocol.class, receiver);
                workerProcess.getConnection().useJavaSerializationForParameters(workerImplementation.getClassLoader());
                workerProcess.getConnection().useParameterSerializers(RequestSerializerRegistry.create(workerImplementation.getClassLoader(), argumentSerializers));

                requestProtocol = workerProcess.getConnection().addOutgoing(RequestProtocol.class);
                workerProcess.getConnection().connect();
                return workerProcess;
            }

            @Override
            public ExecResult stop() {
                if (requestProtocol != null) {
                    requestProtocol.stop();
                }
                try {
                    return workerProcess.waitForStop();
                } finally {
                    requestProtocol = null;
                }
            }

            @Override
            public void stopNow() {
                try {
                    workerProcess.stopNow();
                } finally {
                    requestProtocol = null;
                }
            }

            @Override
            public OUT run(IN request) {
                requestProtocol.run(new Request(request, CurrentBuildOperationRef.instance().get()));
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
                return Cast.uncheckedNonnullCast(receiver.getNextResult());
            }
        };
    }
}
