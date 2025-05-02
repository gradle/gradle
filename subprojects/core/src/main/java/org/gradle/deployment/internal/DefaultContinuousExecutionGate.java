/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.deployment.internal;

import org.gradle.internal.UncheckedException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultContinuousExecutionGate implements ContinuousExecutionGate {
    private final Lock lock = new ReentrantLock();
    private final Condition opened = lock.newCondition();
    private final List<GateKeeper> gatekeepers = new ArrayList<>();
    private GateKeeper openedBy;

    @Override
    public GateKeeper createGateKeeper() {
        lock.lock();
        try {
            GateKeeper gatekeeper = new DefaultExecutionGateKeeper(this);
            gatekeepers.add(gatekeeper);
            return gatekeeper;
        } finally {
            lock.unlock();
        }
    }

    private void open(GateKeeper gatekeeper) {
        lock.lock();
        try {
            // gate hasn't been opened yet
            if (isClosed()) {
                openedBy = gatekeeper;
                opened.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    private boolean isClosed() {
        return openedBy == null;
    }

    private void close(GateKeeper gatekeeper) {
        lock.lock();
        try {
            // The same gatekeeper that opened it must close it
            if (gatekeeper.equals(openedBy)) {
                openedBy = null;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void waitForOpen() {
        lock.lock();
        try {
            if (!gatekeepers.isEmpty()) {
                while (isClosed()) {
                    // wait for it to open
                    opened.await();
                }
            }
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        return "Gate is " + (!isClosed() ? "open" : "closed");
    }

    private static class DefaultExecutionGateKeeper implements GateKeeper {
        private final DefaultContinuousExecutionGate continuousExecutionGate;

        private DefaultExecutionGateKeeper(DefaultContinuousExecutionGate continuousExecutionGate) {
            this.continuousExecutionGate = continuousExecutionGate;
        }

        @Override
        public void open() {
            continuousExecutionGate.open(this);
        }

        @Override
        public void close() {
            continuousExecutionGate.close(this);
        }
    }
}
