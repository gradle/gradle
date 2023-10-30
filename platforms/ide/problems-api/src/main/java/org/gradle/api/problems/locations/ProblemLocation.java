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

package org.gradle.api.problems.locations;

import org.gradle.api.Incubating;

/**
 * Represents a location information of a problem.
 *
 * @since 8.5
 */
@Incubating
public interface ProblemLocation {

    /**
     * Returns an identifier of the location type.
     * <p>
     * As locations will be serialized into a JSON format,
     * this identifier is used to distinguish between different location types.
     *
     * @since 8.5
     */
    String getType();
}
