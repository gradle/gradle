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

import javax.annotation.Nullable;

/**
 * Interface to implement stealing of {@link TestClassRunInfo} in parallel execution environment
 */

@NonNullApi
public interface TestClassStealer {
    /**
     * add new stealable testClass to planned classes, wich can be "stolen"
     *
     * @param testClass added class
     * @param processor processor, from which testClass can be stolen
     */
    void add(TestClassRunInfo testClass, ForkingTestClassProcessor processor);

    /**
     * remove testClass from planned classes when startet
     *
     * @param testClass startet test class
     */
    void remove(TestClassRunInfo testClass);

    /**
     * remove all references to this processor, because he stopped
     * @param processor stopped processor, for which cleanup should be done
     */
    void stopped(ForkingTestClassProcessor processor);

    /**
     * looking for next test-class, that have been successful {@link ForkingTestClassProcessor#handOver hand over}
     *
     * @return next {@link ForkingTestClassProcessor#handOver hand over} test-class or null, if nothing left
     */
    @Nullable
    TestClassRunInfo trySteal();

    /**
     * result of {@link ForkingTestClassProcessor#handOver} request
     *
     * @param forkingTestClassProcessor processor, which should hand over the testClass
     * @param testClass requested test class
     * @param success success of hand over, false means, class was startet since request was initiated
     */
    void handOverTestClass(ForkingTestClassProcessor forkingTestClassProcessor, TestClassRunInfo testClass, boolean success);
}
