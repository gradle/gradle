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

package org.gradle.internal.remote.internal.hub;

import org.gradle.api.Action;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.remote.ConnectionAcceptor;
import org.gradle.internal.remote.MessagingServer;
import org.gradle.internal.remote.internal.IncomingConnector;
import org.gradle.internal.remote.ObjectConnection;
import org.gradle.internal.remote.internal.ConnectCompletion;

public class MessageHubBackedServer implements MessagingServer {
    private final IncomingConnector connector;
    private final ExecutorFactory executorFactory;

    public MessageHubBackedServer(IncomingConnector connector, ExecutorFactory executorFactory) {
        this.connector = connector;
        this.executorFactory = executorFactory;
    }

    @Override
    public ConnectionAcceptor accept(Action<ObjectConnection> action) {
        return connector.accept(new ConnectEventAction(action), false);
    }

    private class ConnectEventAction implements Action<ConnectCompletion> {
        private final Action<ObjectConnection> action;

        public ConnectEventAction(Action<ObjectConnection> action) {
            this.action = action;
        }

        @Override
        public void execute(ConnectCompletion completion) {
            action.execute(new MessageHubBackedObjectConnection(executorFactory, completion));
        }
    }

}
