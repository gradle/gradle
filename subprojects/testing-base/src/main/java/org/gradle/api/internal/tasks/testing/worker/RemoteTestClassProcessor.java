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

package org.gradle.api.internal.tasks.testing.worker;

import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestClassStealer;

/**
 * @see org.gradle.api.internal.tasks.testing.TestClassProcessor
 */
public interface RemoteTestClassProcessor {
    /**
     * Does not block.
     */
    void startProcessing();

    /**
     * Does not block.
     */
    void processTestClass(TestClassRunInfo testClass);

    /**
     * Does not block.
     */
    void stop();

    /** Does not block; test, if testClass is not startet, and send result back to {@link TestClassStealer#handOverTestClass}  */
    void handOverTestClass(TestClassRunInfo testClass);

    /**
     * @param result founded stolen test-class as {@link RemoteStealer.HandOverResult#getTestClass()} and is null, if no more left
     */
    void handedOver(RemoteStealer.HandOverResult result);
}
