/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.process.internal.worker.child;

import org.gradle.api.Action;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.remote.ObjectConnection;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.process.internal.worker.WorkerProcessContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * <p>The final stage of worker start-up. Takes care of executing the worker action.</p>
 *
 * <p>It is instantiated in the implementation ClassLoader and called from {@link SystemApplicationClassLoaderWorker}.<p>
 */
public class ActionExecutionWorker implements Action<WorkerContext>, Serializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActionExecutionWorker.class);
    private final Action<? super WorkerProcessContext> action;
    private final Object workerId;
    private final String displayName;

    public ActionExecutionWorker(Action<? super WorkerProcessContext> action, Object workerId, String displayName) {
        this.action = action;
        this.workerId = workerId;
        this.displayName = displayName;
    }

    @Override
    public void execute(final WorkerContext workerContext) {
        final ObjectConnection clientConnection = workerContext.getServerConnection();
        final ServiceRegistry serviceRegistry = workerContext.getServiceRegistry();
        LOGGER.debug("Starting {}.", displayName);
        WorkerProcessContext context = new WorkerProcessContext() {
            @Override
            public ObjectConnection getServerConnection() {
                return clientConnection;
            }

            @Override
            public ClassLoader getApplicationClassLoader() {
                return workerContext.getApplicationClassLoader();
            }

            @Override
            public Object getWorkerId() {
                return workerId;
            }

            @Override
            public String getDisplayName() {
                return displayName;
            }

            @Override
            public ServiceRegistry getServiceRegistry() {
                return serviceRegistry;
            }
        };

        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(action.getClass().getClassLoader());

        clientConnection.addUnrecoverableErrorHandler(new Action<Throwable>() {
            @Override
            public void execute(Throwable throwable) {
                if (action instanceof Stoppable) {
                    ((Stoppable) action).stop();
                }
            }
        });

        try {
            action.execute(context);
        } finally {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        }
        LOGGER.debug("Completed {}.", displayName);
    }
}
