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

import javax.annotation.Nullable;

/**
 * interface for delegating work stealing
 */
@NonNullApi
public interface RemoteTestClassStealer {
    /**
     * true, if testClass not alsways startet or handed over<br>
     * remove async testClass from central {@link TestClassStealer}
     *
     * @param testClass class that should start
     * @return true, if class can be startet
     */
    boolean canStart(TestClassRunInfo testClass);

    /**
     * Call central TestClassStealer to find next test-class
     *
     * @return found next successful "stolen" test-class or null, if nothing left
     */
    @Nullable
    TestClassRunInfo trySteal();

    /**
     * test if the testClass has been startet, send result to {@link TestClassStealer#handOverTestClass}
     *
     * @param testClass class that should start
     */
    void handOver(TestClassRunInfo testClass);
}
