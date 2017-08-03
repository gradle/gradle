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

package org.gradle.initialization;

import com.google.common.collect.Lists;
import org.gradle.internal.UncheckedException;

import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultBuildGateToken implements BuildGateToken {
    private final Lock lock = new ReentrantLock();
    private final Condition opened = lock.newCondition();
    private final List<GateKeeper> gatekeepers = Lists.newArrayList();
    private GateKeeper openedBy;

    @Override
    public void addGateKeeper(GateKeeper gatekeeper) {
        lock.lock();
        try {
            gatekeepers.add(gatekeeper);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void open(GateKeeper gatekeeper) {
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

    @Override
    public void close(GateKeeper gatekeeper) {
        lock.lock();
        try {
            // The same gatekeeper that opened it, must close it
            if (gatekeeper.equals(openedBy)) {
                openedBy = null;
            }
            System.out.println("closing by " + gatekeeper);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void waitForOpen() {
        lock.lock();
        try {
            if (!gatekeepers.isEmpty()) {
                if (isClosed()) {
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
}
