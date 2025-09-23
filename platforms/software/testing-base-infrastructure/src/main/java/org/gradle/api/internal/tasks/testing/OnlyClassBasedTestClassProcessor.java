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

/**
 * A {@link TestClassProcessor} that only supports class-based test definitions.
 *
 * Implementations of this interface must implement {@link #processClassTestDefinition(ClassTestDefinition)} to process
 * the class-based test definitions.
 *
 * If a non-class-based test definition is passed to {@link #processTestDefinition(TestDefinition)}, a
 * {@link GradleException} will be thrown.
 */
public interface OnlyClassBasedTestClassProcessor extends TestClassProcessor {
    @Override
    default void processTestDefinition(TestDefinition testDefinition) {
        if (ClassTestDefinition.class.isAssignableFrom(testDefinition.getClass())) {
            processClassTestDefinition((ClassTestDefinition) testDefinition);
        } else {
            throw new GradleException(String.format("%s only supports class-based tested, received %s.", this, testDefinition.getClass().getName()));
        }
    }

    /**
     * Processes the given class-based test definition.
     *
     * @param testDefinition The class-based test definition to process.
     */
    void processClassTestDefinition(ClassTestDefinition testDefinition);
}
