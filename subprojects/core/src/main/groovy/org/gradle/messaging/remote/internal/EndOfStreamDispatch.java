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

package org.gradle.messaging.remote.internal;

import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.StoppableDispatch;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class EndOfStreamDispatch implements StoppableDispatch<Object> {
    private final Dispatch<Object> dispatch;
    private boolean stopped;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public EndOfStreamDispatch(Dispatch<Object> dispatch) {
        this.dispatch = dispatch;
    }

    public void dispatch(Object message) {
        lock.readLock().lock();
        try {
            if (stopped) {
                throw new IllegalStateException(String.format(
                        "Cannot dispatch message %s, as this dispatch has been stopped.", message));
            }
            dispatch.dispatch(message);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void stop() {
        lock.writeLock().lock();
        try {
            if (stopped) {
                return;
            }
            stopped = true;
            dispatch.dispatch(new EndOfStreamEvent());
        } finally {
            lock.writeLock().unlock();
        }
    }
}
