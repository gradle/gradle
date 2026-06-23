/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath

import org.jetbrains.annotations.NotNull

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class ClassBasedLock implements Lock {
    private final Class<?> forClass;
    private Lock lock = null

    private static WeakHashMap<Class<?>, Lock> locks = new WeakHashMap<>()

    ClassBasedLock(Class<?> forClass) {
        this.forClass = forClass
    }

    private void initializeLock() {
        if (lock == null) {
            synchronized (ClassBasedLock) {
                lock = locks.computeIfAbsent(forClass) { new ReentrantLock() }
            }
        }
    }

    @Override
    void lock() {
        initializeLock()
        lock.lock()
    }

    void unlock() {
        if (lock == null) {
            throw new IllegalStateException("lock() call not executed")
        }
        lock.unlock()
    }

    @Override
    void lockInterruptibly() throws InterruptedException {
        initializeLock()
        lock.lockInterruptibly()
    }

    @Override
    boolean tryLock() {
        initializeLock()
        return lock.tryLock()
    }

    @Override
    boolean tryLock(long time, @NotNull TimeUnit unit) throws InterruptedException {
        initializeLock()
        return tryLock(time, unit)
    }

    @Override
    Condition newCondition() {
        initializeLock()
        return lock.newCondition()
    }
}
