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

package org.gradle.process.internal;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.UncheckedException;
import org.gradle.messaging.remote.ObjectConnection;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

class DefaultSingleUseWorkerProcessBuilder<T> implements SingleUseWorkerProcessBuilder<T> {
    private final Class<T> protocolType;
    private final DefaultWorkerProcessBuilder builder;

    public DefaultSingleUseWorkerProcessBuilder(Class<T> protocolType, Class<? extends T> workerImplementation, DefaultWorkerProcessBuilder builder) {
        this.protocolType = protocolType;
        this.builder = builder;
        builder.worker(new WorkerAction(workerImplementation));
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
    public T build() {
        return protocolType.cast(Proxy.newProxyInstance(protocolType.getClassLoader(), new Class[]{protocolType}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                WorkerProcess workerProcess = builder.build();
                workerProcess.start();
                ObjectConnection connection = workerProcess.getConnection();
                RequestProtocol requestProtocol = connection.addOutgoing(RequestProtocol.class);
                Receiver receiver = new Receiver();
                connection.addIncoming(ResponseProtocol.class, receiver);
                connection.connect();
                requestProtocol.run(method.getName(), method.getParameterTypes(), args);
                receiver.waitForResult();
                workerProcess.waitForStop();
                return receiver.getResult();
            }
        }));
    }

    interface RequestProtocol {
        void run(String methodName, Class<?>[] paramTypes, Object[] args);
    }

    interface ResponseProtocol {
        void completed(Object result);

        void failed(Throwable failure);
    }

    private static class Receiver implements ResponseProtocol {
        private final CountDownLatch received = new CountDownLatch(1);
        private Object result;
        private Throwable failure;

        void waitForResult() throws Throwable {
            received.await();
        }

        Object getResult() throws Throwable {
            received.await();
            if (failure != null) {
                throw failure;
            }
            return result;
        }

        @Override
        public void completed(Object result) {
            this.result = result;
            received.countDown();
        }

        @Override
        public void failed(Throwable failure) {
            this.failure = failure;
            received.countDown();
        }
    }

    static class WorkerAction implements Action<WorkerProcessContext>, Serializable, RequestProtocol {
        private final Class<?> workerImplementation;
        private CountDownLatch completed;
        private ResponseProtocol responder;

        WorkerAction(Class<?> workerImplementation) {
            this.workerImplementation = workerImplementation;
        }

        @Override
        public void execute(WorkerProcessContext workerProcessContext) {
            completed = new CountDownLatch(1);
            ObjectConnection connection = workerProcessContext.getServerConnection();
            connection.addIncoming(RequestProtocol.class, this);
            responder = connection.addOutgoing(ResponseProtocol.class);
            connection.connect();
            try {
                completed.await();
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        @Override
        public void run(String methodName, Class<?>[] paramTypes, Object[] args) {
            try {
                Object implementation = workerImplementation.newInstance();
                Method method = workerImplementation.getDeclaredMethod(methodName, paramTypes);
                Object result;
                try {
                    result = method.invoke(implementation, args);
                } catch (InvocationTargetException e) {
                    responder.failed(e.getCause());
                    return;
                }
                responder.completed(result);
            } catch (Exception e) {
                responder.failed(new GradleException(String.format("Failed to call method %s on worker implementation %s.", methodName, workerImplementation), e));
            } finally {
                completed.countDown();
            }
        }
    }
}
