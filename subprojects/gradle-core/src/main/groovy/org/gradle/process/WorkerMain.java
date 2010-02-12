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
package org.gradle.process;

import org.gradle.api.Action;
import org.gradle.messaging.MessagingClient;
import org.gradle.messaging.ObjectConnection;
import org.gradle.messaging.TcpMessagingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * This is the other half of {@link org.gradle.process.GradleWorkerMain}. It is instantiated using the implementation
 * ClassLoader.
 */
public class WorkerMain implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerMain.class);
    private final Action<WorkerProcessContext> action;
    private final MessagingClient client;

    public WorkerMain(Action<WorkerProcessContext> action, URI serverAddress) {
        this.action = action;
        client = new TcpMessagingClient(getClass().getClassLoader(), serverAddress);
    }

    WorkerMain(Action<WorkerProcessContext> action, MessagingClient client) {
        this.action = action;
        this.client = client;
    }

    public void run() {
        try {
            WorkerProcessContext context = new WorkerProcessContext() {
                public ObjectConnection getServerConnection() {
                    return client.getConnection();
                }
            };
            LOGGER.debug("Starting worker action.");
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(action.getClass().getClassLoader());
            try {
                action.execute(context);
            } finally {
                Thread.currentThread().setContextClassLoader(contextClassLoader);
            }
            LOGGER.debug("Completed worker action.");
        } finally {
            LOGGER.debug("Stopping client connection.");
            client.stop();
        }
    }
}
