/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.work;

import com.google.common.collect.Iterables;
import org.gradle.api.Describable;
import org.gradle.internal.Factory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation of {@link ResourceLockStatistics} that measures the time spent waiting for resource locks
 * and also wraps all lock operations in build operations.
 */
public class DefaultResourceLockStatistics implements ResourceLockStatistics {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultResourceLockStatistics.class);

    private final AtomicLong totalBlockedTime = new AtomicLong(-1);
    private final BuildOperationRunner buildOperationRunner;

    public DefaultResourceLockStatistics(BuildOperationRunner buildOperationRunner) {
        this.buildOperationRunner = buildOperationRunner;
    }

    @Override
    public void measureLockAcquisition(Iterable<? extends Describable> locks, Runnable runnable) {
        measure("Blocked on", locks, (Factory<@Nullable Void>) () -> {
            Timer timer = Time.startTimer();
            runnable.run();
            totalBlockedTime.addAndGet(timer.getElapsedMillis());
            return null;
        });
    }

    @Override
    public <T extends @Nullable Object> T measure(String operation, Iterable<? extends Describable> locks, Factory<T> factory) {
        return buildOperationRunner.call(new CallableBuildOperation<T>() {
            @Override
            public T call(BuildOperationContext context) {
                return factory.create();
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor
                    .displayName(operation + " [" + String.join(", ", Iterables.transform(locks, Describable::getDisplayName)) + "]");
            }
        });
    }

    @Override
    public void complete() {
        LOGGER.warn("Time spent waiting on resource locks: {}ms", totalBlockedTime.get());
    }

}
