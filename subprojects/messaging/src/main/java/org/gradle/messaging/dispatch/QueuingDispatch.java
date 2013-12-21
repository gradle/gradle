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
package org.gradle.messaging.dispatch;

import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.UncheckedException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Queues messages until a receiver has been connected. This class is thread-safe.
 */
public class QueuingDispatch<T> implements Dispatch<T>, Stoppable {
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private List<T> queue = new ArrayList<T>();
    private Dispatch<? super T> dispatch;

    /**
     * Dispatches to the given handler. The handler does not have to be thread-safe.
     */
    public void dispatchTo(Dispatch<? super T> dispatch) {
        lock.lock();
        try {
            this.dispatch = dispatch;
            for (T message : queue) {
                dispatch.dispatch(message);
            }
            queue = null;
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void dispatch(T message) {
        lock.lock();
        try {
            if (dispatch == null) {
                queue.add(message);
            } else {
                dispatch.dispatch(message);
            }
        } finally {
            lock.unlock();
        }
    }

    public void stop() {
        lock.lock();
        try {
            while (queue != null && !queue.isEmpty()) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        } finally {
            lock.unlock();
        }
    }
}
