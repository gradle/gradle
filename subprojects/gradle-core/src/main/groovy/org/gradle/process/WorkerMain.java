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

import java.net.URI;

public class WorkerMain implements Runnable {
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
            action.execute(context);
        } finally {
            client.stop();
        }
    }
}
