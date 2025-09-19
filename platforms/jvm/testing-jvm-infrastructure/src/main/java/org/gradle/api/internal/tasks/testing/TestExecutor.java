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

import org.jspecify.annotations.NullMarked;

/**
 * A type that executes tests.
 * <p>
 * Used by JUnit and JUnit Platform test frameworks.
 * <p>
 * Replaces {@code Action<String>} with a named type for better discoverability and to allow for future expansion.
 */
@NullMarked
public interface TestExecutor {
    /**
     * Executes a class-based test given its resource name.
     *
     * @param testClassName The FQN of the test class to execute
     */
    default void executeClass(String testClassName) {
        throw new UnsupportedOperationException("Class-Based Testing is not supported by this TestExecutor.");
    }
    
    /**
     * Executes the tests in the given resource.
     *
     * @param resourceFile The test resource file to execute.
     */
    default void executeResource(File resourceFile) {
        throw new UnsupportedOperationException("Resource-Based Testing is not supported by this TestExecutor.");
    }
}
