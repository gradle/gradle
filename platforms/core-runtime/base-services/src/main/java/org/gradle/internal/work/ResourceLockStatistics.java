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

import org.gradle.api.Describable;
import org.gradle.internal.Factory;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

/**
 * Responsible for collecting statistics about resource lock acquisition and usage
 * in a Gradle build invocation.
 */
@ServiceScope(Scope.CrossBuildSession.class)
public interface ResourceLockStatistics {

    /**
     * A {@link ResourceLockStatistics} which does not collect any statistics.
     */
    ResourceLockStatistics NO_OP = new ResourceLockStatistics() {
        @Override
        public void measureLockAcquisition(Iterable<? extends Describable> locks, Runnable runnable) {
            runnable.run();
        }

        @Override
        public <T> T measure(String operation, Iterable<? extends Describable> locks, Factory<T> factory) {
            return factory.create();
        }

        @Override
        public void complete() {

        }
    };

    /**
     * Measures the acquisition the specified locks.
     *
     * @param locks the resources being acquired
     * @param runnable the action which acquires the locks
     */
    void measureLockAcquisition(Iterable<? extends Describable> locks, Runnable runnable);

    /**
     * Measures an operation performed on some locks.
     *
     * @param operation the name of the operation being performed
     * @param locks the resources which the operation is performed on
     * @param factory a factory which performs the operation
     */
    <T extends @Nullable Object> T measure(String operation, Iterable<? extends Describable> locks, Factory<T> factory);

    /**
     * Called when the statistics collection is complete and any results should be reported.
     */
    void complete();

}
