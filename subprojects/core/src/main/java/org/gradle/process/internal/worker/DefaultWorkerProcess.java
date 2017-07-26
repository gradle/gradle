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

package org.gradle.process.internal.worker;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.AsyncStoppable;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.remote.ConnectionAcceptor;
import org.gradle.internal.remote.ObjectConnection;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecException;
import org.gradle.process.internal.ExecHandle;
import org.gradle.process.internal.ExecHandleListener;
import org.gradle.process.internal.health.memory.JvmMemoryStatus;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.String.format;

public class DefaultWorkerProcess implements WorkerProcess {
    private final static Logger LOGGER = Logging.getLogger(DefaultWorkerProcess.class);
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private ObjectConnection connection;
    private ConnectionAcceptor acceptor;
    private ExecHandle execHandle;
    private boolean running;
    private Throwable processFailure;
    private final long connectTimeout;
    private final JvmMemoryStatus jvmMemoryStatus;

    public DefaultWorkerProcess(int connectTimeoutValue, TimeUnit connectTimeoutUnits, @Nullable JvmMemoryStatus jvmMemoryStatus) {
        connectTimeout = connectTimeoutUnits.toMillis(connectTimeoutValue);
        this.jvmMemoryStatus = jvmMemoryStatus;
    }

    @Override
    public JvmMemoryStatus getJvmMemoryStatus() {
        if (jvmMemoryStatus != null) {
            return jvmMemoryStatus;
        } else {
            throw new UnsupportedOperationException("This worker process does not support reporting JVM memory status.");
        }
    }

    public void setExecHandle(ExecHandle execHandle) {
        this.execHandle = execHandle;
        execHandle.addListener(new ExecHandleListener() {
            public void executionStarted(ExecHandle execHandle) {
            }

            public void executionFinished(ExecHandle execHandle, ExecResult execResult) {
                onProcessStop(execResult);
            }
        });
    }

    public void startAccepting(ConnectionAcceptor acceptor) {
        lock.lock();
        try {
            this.acceptor = acceptor;
        } finally {
            lock.unlock();
        }
    }

    public void onConnect(ObjectConnection connection) {
        onConnect(connection, null);
    }

    public void onConnect(ObjectConnection connection, Runnable connectionHandler) {
        AsyncStoppable stoppable;
        lock.lock();
        try {
            LOGGER.debug("Received connection {} from {}", connection, execHandle);

            if (connectionHandler != null && running) {
                connectionHandler.run();
            }

            this.connection = connection;
            condition.signalAll();
            stoppable = acceptor;
        } finally {
            lock.unlock();
        }

        if (stoppable != null) {
            stoppable.requestStop();
        }
    }

    private void onProcessStop(ExecResult execResult) {
        lock.lock();
        try {
            try {
                execResult.rethrowFailure().assertNormalExitValue();
            } catch (Throwable e) {
                processFailure = e;
            }
            running = false;
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        return "DefaultWorkerProcess{"
                + "running=" + running
                + ", execHandle=" + execHandle
                + '}';
    }

    public ObjectConnection getConnection() {
        return connection;
    }

    public WorkerProcess start() {
        try {
            doStart();
        } catch (Throwable t) {
            cleanup();
            throw UncheckedException.throwAsUncheckedException(t);
        }
        return this;
    }

    private void doStart() {
        lock.lock();
        try {
            running = true;
        } finally {
            lock.unlock();
        }

        execHandle.start();

        Date connectExpiry = new Date(System.currentTimeMillis() + connectTimeout);
        lock.lock();
        try {
            while (connection == null && running) {
                try {
                    if (!condition.awaitUntil(connectExpiry)) {
                        throw new ExecException(format("Unable to connect to the child process '%s'.\n"
                                + "It is likely that the child process have crashed - please find the stack trace in the build log.\n"
                                + "This exception might occur when the build machine is extremely loaded.\n"
                                + "The connection attempt hit a timeout after %.1f seconds (last known process state: %s, running: %s).", execHandle, ((double) connectTimeout) / 1000, execHandle.getState(), running));
                    }
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
            if (connection == null) {
                if (processFailure != null) {
                    throw UncheckedException.throwAsUncheckedException(processFailure);
                } else {
                    throw new ExecException(format("Never received a connection from %s.", execHandle));
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public ExecResult waitForStop() {
        try {
            return execHandle.waitForFinish().assertNormalExitValue();
        } finally {
            cleanup();
        }
    }

    private void cleanup() {
        CompositeStoppable stoppable;
        execHandle.abort();
        lock.lock();
        try {
            stoppable = CompositeStoppable.stoppable(acceptor, connection);
        } finally {
            this.connection = null;
            this.acceptor = null;
            lock.unlock();
        }
        stoppable.stop();
    }
}
