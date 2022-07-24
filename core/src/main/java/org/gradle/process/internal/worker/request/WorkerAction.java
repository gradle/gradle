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

package org.gradle.process.internal.worker.request;

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.properties.annotations.OutputPropertyRoleAnnotationHandler;
import org.gradle.cache.internal.DefaultCrossBuildInMemoryCacheFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.dispatch.StreamCompletion;
import org.gradle.internal.event.DefaultListenerManager;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.instantiation.generator.DefaultInstantiatorFactory;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.remote.ObjectConnection;
import org.gradle.internal.remote.internal.hub.StreamFailureHandler;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.Scope.Global;
import org.gradle.process.internal.worker.RequestHandler;
import org.gradle.process.internal.worker.WorkerProcessContext;
import org.gradle.process.internal.worker.child.WorkerLogEventListener;

import java.io.Serializable;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

public class WorkerAction implements Action<WorkerProcessContext>, Serializable, RequestProtocol, StreamFailureHandler, Stoppable, StreamCompletion {
    private final String workerImplementationName;
    private transient CountDownLatch completed;
    private transient ResponseProtocol responder;
    private transient WorkerLogEventListener workerLogEventListener;
    private transient RequestHandler<Object, Object> implementation;
    private transient InstantiatorFactory instantiatorFactory;
    private transient Exception failure;

    public WorkerAction(Class<?> workerImplementation) {
        this.workerImplementationName = workerImplementation.getName();
    }

    @Override
    public void execute(WorkerProcessContext workerProcessContext) {
        completed = new CountDownLatch(1);

        RequestArgumentSerializers argumentSerializers = new RequestArgumentSerializers();
        try {
            ServiceRegistry parentServices = workerProcessContext.getServiceRegistry();
            if (instantiatorFactory == null) {
                instantiatorFactory = new DefaultInstantiatorFactory(new DefaultCrossBuildInMemoryCacheFactory(new DefaultListenerManager(Global.class)), Collections.emptyList(), new OutputPropertyRoleAnnotationHandler(Collections.emptyList()));
            }
            DefaultServiceRegistry serviceRegistry = new DefaultServiceRegistry("worker-action-services", parentServices);
            // Make the argument serializers available so work implementations can register their own serializers
            serviceRegistry.add(RequestArgumentSerializers.class, argumentSerializers);
            serviceRegistry.add(InstantiatorFactory.class, instantiatorFactory);
            Class<?> workerImplementation = Class.forName(workerImplementationName);
            implementation = Cast.uncheckedNonnullCast(instantiatorFactory.inject(serviceRegistry).newInstance(workerImplementation));
        } catch (Exception e) {
            failure = e;
        }

        ObjectConnection connection = workerProcessContext.getServerConnection();
        connection.addIncoming(RequestProtocol.class, this);
        responder = connection.addOutgoing(ResponseProtocol.class);
        workerLogEventListener = workerProcessContext.getServiceRegistry().get(WorkerLogEventListener.class);
        if (failure == null) {
            connection.useParameterSerializers(RequestSerializerRegistry.create(this.getClass().getClassLoader(), argumentSerializers));
        } else {
            // Discard incoming requests, as the serializers may not have been configured
            connection.useParameterSerializers(RequestSerializerRegistry.createDiscardRequestArg());
            // Notify the client
            responder.infrastructureFailed(failure);
        }

        connection.connect();

        try {
            completed.await();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void stop() {
        completed.countDown();
        CurrentBuildOperationRef.instance().clear();
    }

    @Override
    public void endStream() {
        // This happens when the connection between the worker and the build daemon is closed for some reason,
        // possibly because the build daemon died unexpectedly.
        stop();
    }

    @Override
    public void runThenStop(Request request) {
        try {
            run(request);
        } finally {
            stop();
        }
    }

    @Override
    public void run(Request request) {
        if (failure != null) {
            // Ignore
            return;
        }
        try {
            CurrentBuildOperationRef.instance().set(request.getBuildOperation());
            Object result;
            try {
                // We want to use the responder as the logging protocol object here because log messages from the
                // action will have the build operation associated.  By using the responder, we ensure that all
                // messages arrive on the same incoming queue in the build process and the completed message will only
                // arrive after all log messages have been processed.
                result = workerLogEventListener.withWorkerLoggingProtocol(responder, new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        return implementation.run(request.getArg());
                    }
                });
            } catch (Throwable failure) {
                if (failure instanceof NoClassDefFoundError) {
                    // Assume an infrastructure problem
                    responder.infrastructureFailed(failure);
                } else {
                    responder.failed(failure);
                }
                return;
            }
            responder.completed(result);
        } catch (Throwable t) {
            responder.infrastructureFailed(t);
        } finally {
            CurrentBuildOperationRef.instance().clear();
        }
    }

    @Override
    public void handleStreamFailure(Throwable t) {
        responder.failed(t);
    }
}
