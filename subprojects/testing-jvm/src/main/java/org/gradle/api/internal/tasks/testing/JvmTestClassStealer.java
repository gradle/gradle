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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.testing.worker.ForkingTestClassProcessor;
import org.gradle.api.tasks.testing.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages stealing test-classes not startet from busy processors.<br>
 * <br>
 * Collects planned {@link TestClassRunInfo} with assigned processors and removes it, when started.<br>
 * To steal, it will repeat remove first entry until the other processor
 * {@link ForkingTestClassProcessor#handOver hand it over} or no more entries left.<br>
 * <br>
 * incompatible with {@link Test#getForkEvery()} > 0 and senseless, if {@link Test#getMaxParallelForks()} == 1
 *
 * <ul>
 *     <li>{@link #add} collects all {@link TestClassRunInfo} in incoming order with assigned processor</li>
 *     <li>{@link #remove} removes started {@link TestClassRunInfo} from {@link #plannedClasses}</li>
 *     <li>{@link #trySteal} manages stealing</li>
 *     <li>{@link #handOverTestClass} hand over result received</li>
 *     <li>{@link #stopped} signalling processor stopped, related entries cleaned up</li>
 * </ul>
 */
@NonNullApi
public class JvmTestClassStealer implements TestClassStealer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JvmTestClassStealer.class);
    /**
     * serialize Tasks depending on stealing actions to avoid locking
     */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * planned test-classes with assigned processors, could contain always startet Work
     */
    private final Map<TestClassRunInfo, ForkingTestClassProcessor> plannedClasses = new LinkedHashMap<TestClassRunInfo, ForkingTestClassProcessor>();

    /**
     * remember per target-processor which test-class is waiting for handover result, adding result, will release the trySteal waiting
     */
    private final Map<ForkingTestClassProcessor, Map<TestClassRunInfo, BlockingQueue<Boolean>>> tryStealingMap =
        Collections.synchronizedMap(new HashMap<ForkingTestClassProcessor, Map<TestClassRunInfo, BlockingQueue<Boolean>>>());
    private final Set<ForkingTestClassProcessor> stoppedProcessors = Collections.synchronizedSet(new HashSet<ForkingTestClassProcessor>());
    private final CallNextPlanned callNextPlanned = new CallNextPlanned();

    /**
     * add entry with testClass and processor to {@link #plannedClasses}
     *
     * @param testClass new planned test-class
     * @param processor processor planned to work on the testClass
     */
    @Override
    public void add(final TestClassRunInfo testClass, final ForkingTestClassProcessor processor) {
        executor.submit(new RunAdd(testClass, processor));
    }

    /**
     * removes asynchron testClass from {@link #plannedClasses}
     */
    @Override
    public void remove(final TestClassRunInfo testClass) {
        executor.submit(new RunRemove(testClass));
    }

    @Override
    public void stopped(final ForkingTestClassProcessor processor) {
        if (stoppedProcessors.add(processor)) {
            LOGGER.info("Stealer: clean process {}", processor);
            executor.submit(new RunStopped(processor));
        }
    }

    /**
     * looking for next test-class, that have been successful {@link ForkingTestClassProcessor#handOver hand over}
     *
     * @return next {@link ForkingTestClassProcessor#handOver hand over} test-class or null, if nothing left
     */
    @Override
    @Nullable
    public TestClassRunInfo trySteal() {
        try {
            NextPlanned nextPlanned;
            LOGGER.debug("Stealer: waiting for next...");
            do { // looping over async wait ...
                nextPlanned = executor.submit(callNextPlanned).get();
            } while (nextPlanned != null && !nextPlanned.handOver(this));
            LOGGER.info("Stealer: found {}", nextPlanned);
            return nextPlanned == null ? null : nextPlanned.testClass;
        } catch (InterruptedException e) {
            // ignore and clean the interrupted state
            //noinspection ResultOfMethodCallIgnored
            Thread.interrupted();
        } catch (Exception e) { // ExecutionException | ReflectiveOperationException only since java 1.7
            // ignore
        }
        return null;
    }

    @Override
    public void handOverTestClass(ForkingTestClassProcessor forkingTestClassProcessor, TestClassRunInfo testClass, boolean success) {
        LOGGER.debug("Stealer: handOver {} {}", testClass.getTestClassName(), success);
        synchronized (tryStealingMap) {
            final Map<TestClassRunInfo, BlockingQueue<Boolean>> map = tryStealingMap.get(forkingTestClassProcessor);
            if (map == null) {
                return;
            }
            final BlockingQueue<Boolean> queue = map.get(testClass);
            if (queue != null) {
                queue.add(success);
            }
        }
    }

    @NonNullApi
    private static class NextPlanned {
        private final ForkingTestClassProcessor processor;
        private final TestClassRunInfo testClass;
        private final BlockingQueue<Boolean> queue;

        public NextPlanned(Map.Entry<TestClassRunInfo, ForkingTestClassProcessor> entry, BlockingQueue<Boolean> queue) {
            this.processor = entry.getValue();
            this.testClass = entry.getKey();
            this.queue = queue;
        }

        @Override
        public String toString() {
            return String.format("%s from %s", testClass.getTestClassName(), processor);
        }

        private boolean handOver(JvmTestClassStealer stealer) {
            try {
                LOGGER.debug("Stealer: wait for handOver {} from {}", testClass.getTestClassName(), processor);
                processor.handOver(testClass);
                return queue.take(); // stop searching if found
            } catch (Exception ignore) {
                // if anything go wrong, take next
                return true;
            } finally {
                synchronized (stealer.tryStealingMap) {
                    final Map<TestClassRunInfo, BlockingQueue<Boolean>> map = stealer.tryStealingMap.get(processor);
                    if (map != null) {
                        map.remove(testClass);
                        if (map.isEmpty()) {
                            stealer.tryStealingMap.remove(processor);
                        }
                    }
                }
            }
        }
    }

    @NonNullApi
    private class RunAdd implements Runnable, Serializable {
        private final TestClassRunInfo testClass;
        private final ForkingTestClassProcessor processor;

        public RunAdd(TestClassRunInfo testClass, ForkingTestClassProcessor processor) {
            this.testClass = testClass;
            this.processor = processor;
        }

        @Override
        public void run() {
            plannedClasses.put(testClass, processor);
        }
    }

    @NonNullApi
    private class RunRemove implements Runnable, Serializable {
        private final TestClassRunInfo testClass;

        public RunRemove(TestClassRunInfo testClass) {
            this.testClass = testClass;
        }

        @Override
        public void run() {
            plannedClasses.remove(testClass);
        }
    }

    @NonNullApi
    private class CallNextPlanned implements Callable<NextPlanned>, Serializable {
        public CallNextPlanned() {
            // required to not produce subclass failing InternalNullabilityTest
        }

        @Override
        @Nullable
        public NextPlanned call() {
            if (plannedClasses.isEmpty()) {
                return null;
            }
            final Iterator<Map.Entry<TestClassRunInfo, ForkingTestClassProcessor>> iterator = plannedClasses.entrySet().iterator();
            try {
                final ArrayBlockingQueue<Boolean> queue;
                final Map.Entry<TestClassRunInfo, ForkingTestClassProcessor> entry = iterator.next();
                synchronized (tryStealingMap) {
                    Map<TestClassRunInfo, BlockingQueue<Boolean>> remoteStealings = tryStealingMap.get(entry.getValue());
                    if (remoteStealings == null) {
                        remoteStealings = new HashMap<TestClassRunInfo, BlockingQueue<Boolean>>();
                        tryStealingMap.put(entry.getValue(), remoteStealings);
                    }
                    queue = new ArrayBlockingQueue<Boolean>(1);
                    remoteStealings.put(entry.getKey(), queue);
                }
                return new NextPlanned(entry, queue);
            } finally {
                iterator.remove();
            }
        }
    }

    @NonNullApi
    private class RunStopped implements Runnable {
        private final ForkingTestClassProcessor processor;

        public RunStopped(ForkingTestClassProcessor processor) {
            this.processor = processor;
        }

        @Override
        public void run() {
            if (!plannedClasses.isEmpty()) {
                for (final Iterator<Map.Entry<TestClassRunInfo, ForkingTestClassProcessor>> iterator = plannedClasses.entrySet().iterator(); iterator.hasNext();) {
                    final Map.Entry<TestClassRunInfo, ForkingTestClassProcessor> entry = iterator.next();
                    if (entry.getValue() == processor) {
                        iterator.remove();
                    }
                }
            }
            synchronized (tryStealingMap) {
                final Map<TestClassRunInfo, BlockingQueue<Boolean>> waitingMap = tryStealingMap.get(processor);
                if (waitingMap != null) {
                    for (BlockingQueue<Boolean> queue : waitingMap.values()) {
                        queue.add(false);
                    }
                }
            }
        }
    }
}
