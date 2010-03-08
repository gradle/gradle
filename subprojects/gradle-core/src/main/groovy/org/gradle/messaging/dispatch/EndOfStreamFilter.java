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
package org.gradle.messaging.dispatch;

import org.gradle.api.GradleException;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class EndOfStreamFilter implements Dispatch<Message>, Stoppable {
    private final Dispatch<Message> dispatch;
    private final Runnable endOfStreamAction;
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private boolean endOfStreamReached;

    public EndOfStreamFilter(Dispatch<Message> dispatch, Runnable endOfStreamAction) {
        this.dispatch = dispatch;
        this.endOfStreamAction = endOfStreamAction;
    }

    public void dispatch(Message message) {
        lock.lock();
        try {
            if (endOfStreamReached) {
                throw new IllegalStateException(String.format(
                        "Cannot dispatch message %s, as this dispatch has been stopped.", message));
            }
            if (message instanceof EndOfStream) {
                endOfStreamReached = true;
                condition.signalAll();
                endOfStreamAction.run();
                return;
            }
        } finally {
            lock.unlock();
        }
        dispatch.dispatch(message);
    }

    public void stop() {
        lock.lock();
        try {
            while (!endOfStreamReached) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw new GradleException(e);
                }
            }
        } finally {
            lock.unlock();
        }
    }
}
