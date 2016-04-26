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

package org.gradle.launcher.daemon.server.health.gc;

import com.google.common.collect.Sets;

import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultSlidingWindow<T> implements SlidingWindow<T> {
    final LinkedBlockingDeque<T> deque;
    final ReentrantLock lock = new ReentrantLock();

    public DefaultSlidingWindow(int capacity) {
        this.deque = new LinkedBlockingDeque<T>(capacity);
    }

    @Override
    public void slideAndInsert(T element) {
        lock.lock();
        try {
            while (!deque.offerLast(element)) {
                deque.remove();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Set<T> snapshot() {
        lock.lock();
        try {
            return Sets.newLinkedHashSet(deque);
        } finally {
            lock.unlock();
        }
    }
}
