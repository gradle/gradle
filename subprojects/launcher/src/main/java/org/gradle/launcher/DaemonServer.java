/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher;

import org.gradle.api.Action;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.messaging.concurrent.CompositeStoppable;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.ConnectEvent;
import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.internal.DefaultMessageSerializer;
import org.gradle.messaging.remote.internal.inet.InetAddressFactory;
import org.gradle.messaging.remote.internal.inet.TcpIncomingConnector;
import org.gradle.util.UUIDGenerator;

public class DaemonServer {

    private static final Logger LOGGER = Logging.getLogger(DaemonServer.class);
    
    final private DaemonRegistry daemonRegistry;

    public DaemonServer(DaemonRegistry daemonRegistry) {
        this.daemonRegistry = daemonRegistry;
    }

    /**
     * Starts accepting connections.
     */
    void accept(int idleTimeout, final IncomingConnectionHandler handler) {
        DefaultExecutorFactory executorFactory = new DefaultExecutorFactory();
        TcpIncomingConnector<Object> incomingConnector = new TcpIncomingConnector<Object>(executorFactory, new DefaultMessageSerializer<Object>(getClass().getClassLoader()), new InetAddressFactory(), new UUIDGenerator());

        final CompletionHandler finished = new CompletionHandler(idleTimeout);

        LOGGER.lifecycle("Awaiting requests.");

        Action<ConnectEvent<Connection<Object>>> connectEvent = new Action<ConnectEvent<Connection<Object>>>() {
            public void execute(ConnectEvent<Connection<Object>> connectionConnectEvent) {
                handler.handle(connectionConnectEvent.getConnection(), finished);
            }
        };
        final Address address = incomingConnector.accept(connectEvent, false);

        finished.setActivityListener(new CompletionHandler.ActivityListener() {
            public void onStart() {
                daemonRegistry.markBusy(address);
            }

            public void onComplete() {
                daemonRegistry.markIdle(address);
            }
        });

        daemonRegistry.store(address);

        boolean stopped = finished.awaitStop();
        if (!stopped) {
            LOGGER.lifecycle("Time-out waiting for requests. Stopping.");
        }
        daemonRegistry.remove(address);
        new CompositeStoppable(incomingConnector, executorFactory).stop();
    }

}
