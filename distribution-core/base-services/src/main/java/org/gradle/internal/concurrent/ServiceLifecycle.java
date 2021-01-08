/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.concurrent;

import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages the lifecycle of some thread-safe service or resource.
 */
public class ServiceLifecycle implements AsyncStoppable {
    private enum State {RUNNING, STOPPING, STOPPED}

    private final String displayName;
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private State state = State.RUNNING;
    private Map<Thread, Integer> usages = new HashMap<Thread, Integer>();

    public ServiceLifecycle(String displayName) {
        this.displayName = displayName;
    }

    public void use(Runnable runnable) {
        use(Factories.toFactory(runnable));
    }

    public <T> T use(Factory<T> factory) {
        lock.lock();
        try {
            switch (state) {
                case STOPPING:
                    throw new IllegalStateException(String.format("Cannot use %s as it is currently stopping.", displayName));
                case STOPPED:
                    throw new IllegalStateException(String.format("Cannot use %s as it has been stopped.", displayName));
            }
            Integer depth = usages.get(Thread.currentThread());
            if (depth == null) {
                usages.put(Thread.currentThread(), 1);
            } else {
                usages.put(Thread.currentThread(), depth + 1);
            }
        } finally {
            lock.unlock();
        }

        try {
            return factory.create();
        } finally {
            lock.lock();
            try {
                Integer depth = usages.remove(Thread.currentThread());
                if (depth > 1) {
                    usages.put(Thread.currentThread(), depth - 1);
                }
                if (usages.isEmpty()) {
                    condition.signalAll();
                    if (state == State.STOPPING) {
                        state = State.STOPPED;
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public void requestStop() {
        lock.lock();
        try {
            if (state == State.RUNNING) {
                if (usages.isEmpty()) {
                    state = State.STOPPED;
                } else {
                    state = State.STOPPING;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void stop() {
        lock.lock();
        try {
            if (usages.containsKey(Thread.currentThread())) {
                throw new IllegalStateException(String.format("Cannot stop %s from a thread that is using it.", displayName));
            }
            if (state == State.RUNNING) {
                state = State.STOPPING;
            }
            while (!usages.isEmpty()) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
            if (state != State.STOPPED) {
                state = State.STOPPED;
            }
        } finally {
            lock.unlock();
        }
    }
}
