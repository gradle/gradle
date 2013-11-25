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
import org.gradle.internal.TimeProvider;
import org.gradle.internal.UncheckedException;

import java.util.Date;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DelayedReceive<T> implements Stoppable, Receive<T> {
    private final TimeProvider timeProvider;
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final PriorityQueue<DelayedMessage> queue = new PriorityQueue<DelayedMessage>();
    private boolean stopping;

    public DelayedReceive(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    public T receive() {
        lock.lock();
        try {
            while (true) {
                DelayedMessage message = queue.peek();
                if (message == null && stopping) {
                    return null;
                }
                if (message == null) {
                    condition.await();
                    continue;
                }

                long now = timeProvider.getCurrentTime();
                if (message.dispatchTime > now) {
                    condition.awaitUntil(new Date(message.dispatchTime));
                } else {
                    queue.poll();
                    if (queue.isEmpty()) {
                        condition.signalAll();
                    }
                    return message.message;
                }
            }
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Dispatches the given message after the given delay.
     */
    public void dispatchLater(T message, int delayValue, TimeUnit delayUnits) {
        long dispatchTime = timeProvider.getCurrentTime() + delayUnits.toMillis(delayValue);
        lock.lock();
        try {
            if (stopping) {
                throw new IllegalStateException("This dispatch has been stopped.");
            }
            queue.add(new DelayedMessage(dispatchTime, message));
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes one instance of the given message from the queue.
     *
     * @return true if removed, false if not. If false is returned, the message may be currently being dispatched.
     */
    public boolean remove(T message) {
        lock.lock();
        try {
            Iterator<DelayedMessage> iterator = queue.iterator();
            while (iterator.hasNext()) {
                DelayedMessage next = iterator.next();
                if (next.message.equals(message)) {
                    iterator.remove();
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes all queued messages.
     */
    public void clear() {
        lock.lock();
        try {
            queue.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Blocks until all queued messages are delivered.
     */
    public void stop() {
        lock.lock();
        try {
            stopping = true;
            condition.signalAll();
            while (!queue.isEmpty()) {
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

    private class DelayedMessage implements Comparable<DelayedMessage> {
        private final long dispatchTime;
        private final T message;

        private DelayedMessage(long dispatchTime, T message) {
            this.dispatchTime = dispatchTime;
            this.message = message;
        }

        public int compareTo(DelayedMessage delayedMessage) {
            if (dispatchTime > delayedMessage.dispatchTime) {
                return 1;
            } else if (dispatchTime < delayedMessage.dispatchTime) {
                return -1;
            }
            return 0;
        }
    }
}
