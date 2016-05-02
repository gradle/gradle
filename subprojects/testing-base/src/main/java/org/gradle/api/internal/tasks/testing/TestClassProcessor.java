/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.internal.concurrent.Stoppable;

/**
 * A processor for executing tests. Implementations are not required to be thread-safe.
 */
public interface TestClassProcessor extends Stoppable {
    /**
     * Performs any initialisation which this processor needs to perform.
     *
     * @param resultProcessor The processor to send results to during test execution.
     */
    void startProcessing(TestResultProcessor resultProcessor);

    /**
     * Accepts the given test class for processing. May execute synchronously, asynchronously, or defer execution for
     * later.
     *
     * @param testClass The test class.
     */
    void processTestClass(TestClassRunInfo testClass);

    /**
     * Completes any pending or asynchronous processing. Blocks until all processing is complete. The processor should
     * not use the result processor provided to {@link #startProcessing(TestResultProcessor)} after this method has
     * returned.
     */
    @Override
    void stop();
}
