/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.services;

import org.gradle.api.Incubating;
import org.jspecify.annotations.Nullable;

/**
 * Provides access to the services that Gradle makes available to build logic, such as
 * {@link org.gradle.api.file.FileSystemOperations} or {@link org.gradle.process.ExecOperations}.
 *
 * <p>This is the counterpart of {@link BuildServiceRegistry shared services}: shared services are
 * registered by build logic, whereas built-in services are provided by Gradle itself.</p>
 *
 * <p>Only <em>public</em> Gradle services can be accessed. Requesting an internal type fails.</p>
 *
 * @since 9.7.0
 */
@Incubating
public interface BuiltInServices {

    /**
     * Returns the built-in service of the given type.
     *
     * @param serviceType the public type of the service
     * @param <T> the service type
     * @return the service, never {@code null}
     * @throws IllegalArgumentException if the type is not a public Gradle service, or no such service is available in this scope
     * @since 9.7.0
     */
    <T> T get(Class<T> serviceType);

    /**
     * Returns the built-in service of the given type, or {@code null} if no such service is available in this scope.
     *
     * @param serviceType the public type of the service
     * @param <T> the service type
     * @return the service, or {@code null} if not available in this scope
     * @throws IllegalArgumentException if the type is not a public Gradle service
     * @since 9.7.0
     */
    <T> @Nullable T find(Class<T> serviceType);
}
