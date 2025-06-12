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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.GradleException;
import org.jspecify.annotations.NullMarked;

/**
 * A {@link TestClassProcessor} that requires a specific testing framework dependency or dependencies to be available
 * at test runtime.
 *
 * Provides a standard way to check if the framework is available before starting the test class processing.
 *
 * @since 9.0
 */
@NullMarked
public interface RequiresTestFrameworkTestClassProcessor extends TestClassProcessor {
    /**
     * Checks if the required framework dependencies for test class processing are available.
     *
     * @throws TestFrameworkNotAvailableException if not
     */
    void assertTestFrameworkAvailable();

    /**
     * A {@link GradleException} thrown when the required test framework dependencies are not available at runtime.
     */
    class TestFrameworkNotAvailableException extends GradleException {
        public TestFrameworkNotAvailableException(String message) {
            super(message);
        }
    }
}
