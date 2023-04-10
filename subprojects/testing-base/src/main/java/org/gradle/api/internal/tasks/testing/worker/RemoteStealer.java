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

package org.gradle.api.internal.tasks.testing.worker;

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestClassStealer;

import javax.annotation.Nullable;
import java.io.Serializable;

/**
 * interface for remote calling {@link TestClassStealer}, calls don't block
 */
@NonNullApi
public interface RemoteStealer extends Serializable {
    /**
     * removes asynchron testClass from plannedClasses
     */
    void remove(TestClassRunInfo testClass);

    /**
     * looking for next test-class, that have been successful {@link ForkingTestClassProcessor#handOver hand over}<br>
     */
    void tryStealing();

    /**
     * result of hand over of giving test-class
     */
    void handOver(HandOverResult result);

    @NonNullApi
    class HandOverResult implements Serializable {
        @Nullable
        private final TestClassRunInfo testClass;
        private final boolean success;

        public HandOverResult(@Nullable TestClassRunInfo testClass, boolean success) {
            this.testClass = testClass;
            this.success = success;
        }

        @Nullable
        public TestClassRunInfo getTestClass() {
            return testClass;
        }

        @Override
        public String toString() {
            return String.format("HandOverResult %s %s", testClass == null ? "null" : testClass.getTestClassName(), success);
        }

        public boolean isSuccess() {
            return success;
        }

    }
}
