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
package org.gradle.launcher;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.messaging.concurrent.CompositeStoppable;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.ConnectEvent;
import org.gradle.messaging.remote.internal.*;
import org.gradle.messaging.remote.internal.inet.InetAddressFactory;
import org.gradle.messaging.remote.internal.inet.TcpIncomingConnector;
import org.gradle.messaging.remote.internal.inet.TcpOutgoingConnector;
import org.gradle.util.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DaemonConnector {
    private static final Logger LOGGER = Logging.getLogger(DaemonConnector.class);
    private final File userHomeDir;

    public DaemonConnector(File userHomeDir) {
        this.userHomeDir = userHomeDir;
    }

    /**
     * Attempts to connect to the daemon, if it is running.
     *
     * @return The connection, or null if not running.
     */
    public Connection<Object> maybeConnect() {
        Address address = loadDaemonAddress();
        if (address == null) {
            return null;
        }
        try {
            return new TcpOutgoingConnector<Object>(new DefaultMessageSerializer<Object>(getClass().getClassLoader())).connect(address);
        } catch (ConnectException e) {
            // Ignore
            return null;
        }
    }

    private Address loadDaemonAddress() {
        try {
            FileInputStream inputStream = new FileInputStream(getRegistryFile());
            try {
                // Acquire shared lock on file while reading it
                inputStream.getChannel().lock(0, Long.MAX_VALUE, true);
                ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                return (Address) objectInputStream.readObject();
            } finally {
                // Also releases the lock
                inputStream.close();
            }
        } catch (FileNotFoundException e) {
            // Ignore
            return null;
        } catch (EOFException e) {
            // Daemon has created empty file, but not yet locked it or written anything to it.
            // Or has crashed while writing the registry file.
            return null;
        } catch (Exception e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }

    /**
     * Connects to the daemon, starting it if required.
     *
     * @return The connection. Never returns null.
     */
    public Connection<Object> connect() {
        Connection<Object> connection = maybeConnect();
        if (connection != null) {
            return connection;
        }

        LOGGER.info("Starting Gradle daemon");
        startDaemon();
        Date expiry = new Date(System.currentTimeMillis() + 30000L);
        do {
            connection = maybeConnect();
            if (connection != null) {
                return connection;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                throw UncheckedException.asUncheckedException(e);
            }
        } while (System.currentTimeMillis() < expiry.getTime());

        throw new GradleException("Timeout waiting to connect to Gradle daemon.");
    }

    private void startDaemon() {
        List<String> daemonArgs = new ArrayList<String>();
        daemonArgs.add(Jvm.current().getJavaExecutable().getAbsolutePath());
        daemonArgs.add("-Xmx1024m");
        daemonArgs.add("-XX:MaxPermSize=256m");
        daemonArgs.add("-cp");
        daemonArgs.add(GUtil.join(new DefaultClassPathRegistry().getClassPathFiles("GRADLE_RUNTIME"),
                File.pathSeparator));
        daemonArgs.add(GradleDaemon.class.getName());
        daemonArgs.add(String.format("-%s", DefaultCommandLineConverter.GRADLE_USER_HOME));
        daemonArgs.add(userHomeDir.getAbsolutePath());
        DaemonStartAction daemon = new DaemonStartAction();
        daemon.args(daemonArgs);
        daemon.workingDir(userHomeDir);
        daemon.start();
    }

    /**
     * Starts accepting connections.
     *
     * @param handler The handler for connections.
     */
    void accept(final IncomingConnectionHandler handler) {
        DefaultExecutorFactory executorFactory = new DefaultExecutorFactory();
        TcpIncomingConnector<Object> incomingConnector = new TcpIncomingConnector<Object>(executorFactory, new DefaultMessageSerializer<Object>(getClass().getClassLoader()), new InetAddressFactory(), new UUIDGenerator());
        final CompletionHandler finished = new CompletionHandler();

        LOGGER.lifecycle("Awaiting requests.");

        Action<ConnectEvent<Connection<Object>>> connectEvent = new Action<ConnectEvent<Connection<Object>>>() {
            public void execute(ConnectEvent<Connection<Object>> connectionConnectEvent) {
                try {
                    finished.onStartActivity();
                    handler.handle(connectionConnectEvent.getConnection(), finished);
                } finally {
                    finished.onActivityComplete();
                    connectionConnectEvent.getConnection().stop();
                }
            }
        };
        Address address = incomingConnector.accept(connectEvent, false);

        storeDaemonAddress(address);

        boolean stopped = finished.awaitStop();
        if (!stopped) {
            LOGGER.lifecycle("Time-out waiting for requests. Stopping.");
        }
        new CompositeStoppable(incomingConnector, executorFactory).stop();

        getRegistryFile().delete();
    }

    private void storeDaemonAddress(Address address) {
        try {
            File registryFile = getRegistryFile();
            registryFile.getParentFile().mkdirs();
            FileOutputStream outputStream = new FileOutputStream(registryFile);
            try {
                // Lock file while writing to it
                outputStream.getChannel().lock();
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
                objectOutputStream.writeObject(address);
                objectOutputStream.flush();
            } finally {
                // Also releases the lock
                outputStream.close();
            }
        } catch (IOException e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }

    private File getRegistryFile() {
        return new File(userHomeDir, String.format("daemon/%s/registry.bin", GradleVersion.current().getVersion()));
    }

    private static class CompletionHandler implements Stoppable {
        private static final int THREE_HOURS = 3 * 60 * 60 * 1000;
        private final Lock lock = new ReentrantLock();
        private final Condition condition = lock.newCondition();
        private boolean running;
        private boolean stopped;
        private long expiry;

        CompletionHandler() {
            resetTimer();
        }

        /**
         * Waits until stopped, or timeout.
         *
         * @return true if stopped, false if timeout
         */
        public boolean awaitStop() {
            lock.lock();
            try {
                while (running || (!stopped && System.currentTimeMillis() < expiry)) {
                    try {
                        if (running) {
                            condition.await();
                        } else {
                            condition.awaitUntil(new Date(expiry));
                        }
                    } catch (InterruptedException e) {
                        throw UncheckedException.asUncheckedException(e);
                    }
                }
                assert !running;
                return stopped;
            } finally {
                lock.unlock();
            }
        }

        public void stop() {
            lock.lock();
            try {
                stopped = true;
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        public void onStartActivity() {
            lock.lock();
            try {
                assert !running;
                running = true;
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        public void onActivityComplete() {
            lock.lock();
            try {
                assert running;
                running = false;
                resetTimer();
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        private void resetTimer() {
            expiry = System.currentTimeMillis() + THREE_HOURS;
        }
    }
}