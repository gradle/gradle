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

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Striped;
import org.gradle.api.GradleException;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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

    /**
     * Creates a {@link ProducerGuard} which guarantees that different keys will block on different locks,
     * ensuring maximum concurrency, but will also timeout when the lock cannot be acquired.
     * This can be used typically when debugging deadlocks. Please notice however that this kind of guard
     * consumes significantly more resources than the {@link #striped() striped} and {@link #adaptive() adaptive}
     * versions.
     * If the timeout is reached, a timeout exception will be thrown.
     */
    public static <T> ProducerGuard<T> timingOut(int value, TimeUnit timeUnit) {
        return new TimingOutAdaptiveProducerGuard<T>(value, timeUnit);
    }

    private ProducerGuard() {

    }

    /**
     * Runs the given factory, guarded by the given key.
     *
     * @param key the key to lock on
     * @param factory the factory to run under the lock
     * @param <V> the type returned by the factory
     * @return the value returned by the factory
     */
    public abstract <V> V guardByKey(T key, Factory<V> factory);

    private static class AdaptiveProducerGuard<T> extends ProducerGuard<T> {
        private final Set<T> producing = Sets.newHashSet();

        @Override
        public <V> V guardByKey(T key, Factory<V> factory) {
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
                return factory.create();
            } finally {
                synchronized (producing) {
                    producing.remove(key);
                    producing.notifyAll();
                }
            }
        }
    }

    private static class TimingOutAdaptiveProducerGuard<T> extends ProducerGuard<T> {
        private final Map<T, CountingLock> producing = Maps.newHashMap();
        private final int timeout;
        private final TimeUnit timeUnit;

        private TimingOutAdaptiveProducerGuard(int timeout, TimeUnit timeUnit) {
            this.timeout = timeout;
            this.timeUnit = timeUnit;
        }

        private CountingLock getLock(T key) {
            synchronized (producing) {
                CountingLock lock = producing.get(key);
                if (lock == null) {
                    lock = new CountingLock();
                    producing.put(key, lock);
                }
                lock.borrow();
                return lock;
            }
        }

        @Override
        public <V> V guardByKey(T key, Factory<V> factory) {
            CountingLock lock = getLock(key);
            boolean locked = false;
            try {
                locked = lock.tryLock(timeout, timeUnit);
                if (!locked) {
                    throw new GradleException("Timed out while trying to acquire a lock on: " + key);
                }
                return factory.create();
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            } finally {
                releaseLock(key, lock, locked);
            }
        }

        private void releaseLock(T key, CountingLock lock, boolean locked) {
            synchronized (producing) {
                if (lock.release(locked)) {
                    producing.remove(key);
                }
            }
        }

        private static class CountingLock {
            private final Lock lock = new ReentrantLock();
            private final AtomicInteger lockCount = new AtomicInteger();

            public boolean tryLock(int value, TimeUnit timeUnit) throws InterruptedException {
                return lock.tryLock(value, timeUnit);
            }

            public void borrow() {
                lockCount.incrementAndGet();
            }

            public boolean release(boolean locked) {
                if (locked) {
                    lock.unlock();
                }
                return lockCount.decrementAndGet() == 0;
            }
        }

    }

    private static class StripedProducerGuard<T> extends ProducerGuard<T> {
        private final Striped<Lock> locks = Striped.lock(Runtime.getRuntime().availableProcessors() * 4);

        @Override
        public <V> V guardByKey(T key, Factory<V> factory) {
            Lock lock = locks.get(key);
            try {
                lock.lock();
                return factory.create();
            } finally {
                lock.unlock();
            }
        }
    }

    private static class SerialProducerGuard<T> extends ProducerGuard<T> {
        private final Lock lock = new ReentrantLock();

        @Override
        public <V> V guardByKey(T key, Factory<V> factory) {
            try {
                lock.lock();
                return factory.create();
            } finally {
                lock.unlock();
            }
        }
    }
}
