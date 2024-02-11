/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.component.resolution.failure.type;

/**
 * Represents a specific type of failure that can occur during dependency resolution and
 * contains contextual information specific to that type of failure.
 *
 * @implSpec Extending types should be immutable data classes with no nullable fields.
 */
public interface ResolutionFailure {
    /**
     * Returns a human-readable name of the requested component or configuration, for use
     * primarily in error messages.
     *
     * @return the name of the requested component or configuration
     */
    String getRequestedName();
}
