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

package org.gradle.platform;

import org.gradle.api.Incubating;
import org.gradle.api.NonNullApi;

/**
 * Represents a function that knows how to provide the current architecture of the platform that Gradle is running on.
 *
 * <p>
 * An instance of this can be injected into a task, plugin or other object by annotating a public constructor or property getter method with {@link javax.inject.Inject}.
 * </p>
 *
 * @since 8.13
 */
@NonNullApi
@Incubating
public interface CurrentArchitectureSupplier {
    /**
     * Returns the current architecture of the platform that Gradle is running on.
     *
     * @return the current architecture
     */
    // Note: this is named `get` to allow extending java.util.function.Supplier in the future, when this class is in a Java 8 module
    Architecture get();
}
