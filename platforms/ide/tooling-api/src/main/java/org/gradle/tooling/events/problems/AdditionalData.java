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

package org.gradle.tooling.events.problems;

import org.gradle.api.Incubating;
import org.gradle.api.NonNullApi;

import java.util.Map;

/**
 * Additional data attached to the problem.
 * <p>
 * There are no subtypes defined for this interface yet. Clients should expect some defined in future versions of Gradle.
 * <p>
 * The information returned by {@code #getAsMap} is considered dynamic information and subject to change between Gradle versions.
 *
 * @since 8.6
 */
@Incubating
@NonNullApi
public interface AdditionalData {

    /**
     * Returns additional data as a map.
     *
     * @since 8.6
     */
    Map<String, Object> getAsMap();
}
