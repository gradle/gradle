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

package org.gradle.api.internal.project;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * TODO SF - find a better place if it is useful
 *
 * by Szczepan Faber, created at: 11/24/11
 */
public class Synchronizer {

    private final Lock lock = new ReentrantLock();

    public <T> T threadSafely(ThreadSafely<T> action) {
        lock.lock();
        try {
            return action.run();
        } finally {
            lock.unlock();
        }
    }

    public static interface ThreadSafely<T> {
        T run();
    }
}