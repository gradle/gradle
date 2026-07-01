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

import org.gradle.api.GradleException;
import org.gradle.api.Incubating;

/**
 * Thrown when a service requested from {@link BuiltInServices} cannot be provided, either because
 * it is not a public Gradle service, or because it is not available in the current scope.
 *
 * <p>This is the public counterpart of the internal service-lookup exceptions.</p>
 *
 * @since 9.7.0
 */
@Incubating
public class InvalidServiceLookupException extends GradleException {

    public InvalidServiceLookupException(String message) {
        super(message);
    }

    public InvalidServiceLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
