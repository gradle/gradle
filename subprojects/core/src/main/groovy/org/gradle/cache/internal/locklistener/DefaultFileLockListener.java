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

package org.gradle.cache.internal.locklistener;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.internal.FileLockCommunicator;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.StoppableExecutor;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * By Szczepan Faber on 5/28/13
 */
//TODO SF if this is ok, let's tighten up the unit test coverage
public class DefaultFileLockListener implements FileLockListener {
    private static final Logger LOGGER = Logging.getLogger(DefaultFileLockListener.class);
    private final Lock lock = new ReentrantLock();
    private final Map<File, Runnable> contendedActions = new HashMap<File, Runnable>();
    private FileLockCommunicator communicator = new FileLockCommunicator();

    private Runnable listener() {
        return new Runnable() {
            public void run() {
                try {
                    LOGGER.info("Starting file lock listener thread.");
                    doRun();
                } catch (Throwable t) {
                    LOGGER.error("Problems handling incoming cache access requests.", t);
                } finally {
                    LOGGER.info("File lock listener thread completed.");
                }
            }

            private void doRun() {
                File requestedFileLock;
                while ((requestedFileLock = communicator.receive()) != null) {
                    lock.lock();
                    Runnable action;
                    try {
                        action = contendedActions.get(requestedFileLock);
                        if (action == null) {
                            return;
                        }
                    } finally {
                        lock.unlock();
                    }

                    action.run();
                }
            }
        };
    }

    private StoppableExecutor executor;

    public void lockCreated(File target, Runnable whenContended) {
        lock.lock();
        try {
            if (contendedActions.isEmpty()) {
                communicator.start();
                executor = new DefaultExecutorFactory().create("Listen for file lock access requests from other processes");
                executor.execute(listener());
            }
            contendedActions.put(target, whenContended);
        } finally {
            lock.unlock();
        }
    }

    public void stopListening(File target) {
        StoppableExecutor stopMe = null;
        lock.lock();
        try {
            contendedActions.remove(target);
            if (contendedActions.isEmpty()) {
                communicator.stop();
                stopMe = executor;
                stopMe.requestStop();
            }
        } finally {
            lock.unlock();
        }
        if (stopMe != null) {
            stopMe.stop();
        }
    }

    public int reservePort() {
        lock.lock();
        try {
            if (!communicator.isStarted()) {
                communicator.start();
            }
            return communicator.getPort();
        } finally {
            lock.unlock();
        }
    }
}
