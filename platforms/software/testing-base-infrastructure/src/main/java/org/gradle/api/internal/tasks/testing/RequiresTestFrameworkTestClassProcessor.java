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
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

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
     * Returns a resolution message pointing to the location in the upgrade guide discussing the
     * need to add test framework dependencies.
     *
     * @return resolution message
     */
    default String getUpgradeGuide() {
        /*
         * Note that we can't use the Documentation class here instead, because we'll get missing Guava classes at runtime:
         *   Caused by: java.lang.ClassNotFoundException: com.google.common.base.Preconditions
         */
        return "See the upgrade guide for more details: " + new DocumentationRegistry().getDocumentationFor("upgrading_version_8", "test_framework_implementation_dependencies");
    }

    /**
     * A {@link GradleException} thrown when the required test framework dependencies are not available at runtime.
     */
    class TestFrameworkNotAvailableException extends GradleException implements ResolutionProvider {
        private final List<String> resolutions;

        public TestFrameworkNotAvailableException(String message, List<String> resolutions) {
            super(message);
            this.resolutions = new ArrayList<>(resolutions);
        }

        @Override
        public List<String> getResolutions() {
            return resolutions;
        }
    }
}
