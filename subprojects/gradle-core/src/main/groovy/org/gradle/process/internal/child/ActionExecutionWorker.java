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

package org.gradle.process.internal.child;

import org.gradle.api.Action;
import org.gradle.messaging.remote.MessagingClient;
import org.gradle.messaging.remote.ObjectConnection;
import org.gradle.messaging.remote.internal.TcpMessagingClient;
import org.gradle.process.internal.WorkerProcessContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.net.URI;

/**
 * <p>The final stage of worker start-up. Takes care of executing the worker action.</p>
 *
 * <p>It is instantiated in the implementation ClassLoader and called from {@link ImplementationClassLoaderWorker}.<p>
 */
public class ActionExecutionWorker implements Action<WorkerContext>, Serializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActionExecutionWorker.class);
    private final Action<WorkerProcessContext> action;
    private final Object workerId;
    private final String displayName;
    private final URI serverAddress;

    public ActionExecutionWorker(Action<WorkerProcessContext> action, Object workerId, String displayName,
                                 URI serverAddress) {
        this.action = action;
        this.workerId = workerId;
        this.displayName = displayName;
        this.serverAddress = serverAddress;
    }

    public void execute(final WorkerContext workerContext) {
        final MessagingClient client = createClient();
        try {
            LOGGER.debug("Starting {}.", displayName);
            WorkerProcessContext context = new WorkerProcessContext() {
                public ObjectConnection getServerConnection() {
                    return client.getConnection();
                }

                public ClassLoader getApplicationClassLoader() {
                    return workerContext.getApplicationClassLoader();
                }

                public Object getWorkerId() {
                    return workerId;
                }

                public String getDisplayName() {
                    return displayName;
                }
            };

            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(action.getClass().getClassLoader());
            try {
                action.execute(context);
            } finally {
                Thread.currentThread().setContextClassLoader(contextClassLoader);
            }
            LOGGER.debug("Completed {}.", displayName);
        } finally {
            LOGGER.debug("Stopping client connection.");
            client.stop();
        }
    }

    MessagingClient createClient() {
        return new TcpMessagingClient(getClass().getClassLoader(), serverAddress);
    }
}
