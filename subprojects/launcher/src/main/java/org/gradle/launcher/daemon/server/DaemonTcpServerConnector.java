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
package org.gradle.launcher.daemon.server;

import org.gradle.api.Action;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.id.UUIDGenerator;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.ConnectionAcceptor;
import org.gradle.messaging.remote.internal.ConnectCompletion;
import org.gradle.messaging.remote.internal.IncomingConnector;
import org.gradle.messaging.remote.internal.inet.InetAddressFactory;
import org.gradle.messaging.remote.internal.inet.TcpIncomingConnector;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Opens a TCP connection for clients to connect to to communicate with a daemon.
 */
public class DaemonTcpServerConnector implements DaemonServerConnector {
    final private IncomingConnector incomingConnector;

    private boolean started;
    private boolean stopped;
    private final Lock lifecycleLock = new ReentrantLock();
    private ConnectionAcceptor acceptor;

    public DaemonTcpServerConnector() {
        this.incomingConnector = new TcpIncomingConnector(
                new DefaultExecutorFactory(),
                new InetAddressFactory(),
                new UUIDGenerator()
        );
    }

    public Address start(final IncomingConnectionHandler handler) {
        lifecycleLock.lock();
        try {
            if (stopped) {
                throw new IllegalStateException("server connector cannot be started as it is either stopping or has been stopped");
            }
            if (started) {
                throw new IllegalStateException("server connector cannot be started as it has already been started");
            }

            // Hold the lock until we actually start accepting connections for the case when stop is called from another
            // thread while we are in the middle here.

            Action<ConnectCompletion> connectEvent = new Action<ConnectCompletion>() {
                public void execute(ConnectCompletion completion) {
                    handler.handle(new SynchronizedDispatchConnection<Object>(completion.create(getClass().getClassLoader())));
                }
            };

            acceptor = incomingConnector.accept(connectEvent, false);
            started = true;
            return acceptor.getAddress();
        } finally {
            lifecycleLock.unlock();
        }
    }

    public void stop() {
        lifecycleLock.lock();
        try {
            stopped = true;
        } finally {
            lifecycleLock.unlock();
        }

        CompositeStoppable.stoppable(acceptor, incomingConnector).stop();
    }

}