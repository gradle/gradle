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
package org.gradle.cache.internal;

import com.google.common.util.concurrent.Striped;
import org.gradle.internal.UncheckedException;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Synchronizes access to some resource, by making sure that 2 threads do not try to produce it at the same time.
 * The resource to be accessed is represented by a key, and the factory is whatever needs to be done to produce it.
 * This is <b>not</b> a cache. The factory should take care of caching wherever it makes sense.
 *
 * <p>
 * The concurrency level and memory usage depend on the implementation, see {@link #adaptive()}, {@link #striped()} and {@link #serial()} for details.
 * </p>
 *
 * @param <T> the type of key to lock on
 */
public abstract class ProducerGuard<T> {

    /**
     * Creates a {@link ProducerGuard} which guarantees that different keys will block on different locks,
     * ensuring maximum concurrency. This comes at the cost of allocating locks for each key, leading to
     * relatively high memory pressure. If the above guarantee is not necessary, consider using a {@link #striped()}
     * guard instead.
     */
    public static <T> ProducerGuard<T> adaptive() {
        return new AdaptiveProducerGuard<T>();
    }

    /**
     * Creates a {@link ProducerGuard} which evenly spreads calls over a fixed number of locks.
     * This means that in some cases two different keys can block on the same lock. The benefit of
     * this strategy is that it uses only a fixed amount of memory. If your code depends on
     * different keys always getting different locks, use a {@link #adaptive()} guard instead.
     */
    public static <T> ProducerGuard<T> striped() {
        return new StripedProducerGuard<T>();
    }

    /**
     * Creates a {@link ProducerGuard} which limits concurrency to a single factory at a time,
     * ignoring the key. This is mainly useful for testing.
     */
    public static <T> ProducerGuard<T> serial() {
        return new SerialProducerGuard<T>();
    }

    private ProducerGuard() {

    }

    /**
     * Runs the given factory, guarded by the given key.
     *
     * @param key the key to lock on
     * @param supplier the supplier to run under the lock
     * @param <V> the type returned by the factory
     * @return the value returned by the factory
     */
    public abstract <V> V guardByKey(T key, Supplier<V> supplier);

    private static class AdaptiveProducerGuard<T> extends ProducerGuard<T> {
        private final Set<T> producing = new HashSet<>();

        @Override
        public <V> V guardByKey(T key, Supplier<V> supplier) {
            synchronized (producing) {
                while (!producing.add(key)) {
                    try {
                        producing.wait();
                    } catch (InterruptedException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }
            }
            try {
                return supplier.get();
            } finally {
                synchronized (producing) {
                    producing.remove(key);
                    producing.notifyAll();
                }
            }
        }
    }

    private static class StripedProducerGuard<T> extends ProducerGuard<T> {
        private final Striped<Lock> locks = Striped.lock(Runtime.getRuntime().availableProcessors() * 4);

        @Override
        public <V> V guardByKey(T key, Supplier<V> supplier) {
            Lock lock = locks.get(key);
            lock.lock();
            try {
                return supplier.get();
            } finally {
                lock.unlock();
            }
        }
    }

    private static class SerialProducerGuard<T> extends ProducerGuard<T> {
        private final Lock lock = new ReentrantLock();

        @Override
        public <V> V guardByKey(T key, Supplier<V> supplier) {
            lock.lock();
            try {
                return supplier.get();
            } finally {
                lock.unlock();
            }
        }
    }
}
