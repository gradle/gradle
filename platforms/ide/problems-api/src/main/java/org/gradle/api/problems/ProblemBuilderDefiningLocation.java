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

package org.gradle.api.problems;

import org.gradle.api.Incubating;

/**
 * {@link Problem} instance builder requiring the specification of the problem location.
 *
 * @since 8.4
 */
@Incubating
public interface ProblemBuilderDefiningLocation {
    /**
     * Declares that this problem is in a file at a particular line.
     *
     * @param path the file location
     * @param line the line number
     * @return the builder for the next required property
     */
    ProblemBuilderDefiningCategory location(String path, Integer line); // TODO rename to fileLocation

    /**
     * Declares that this problem is in a file at a particular line.
     *
     * @param path the file location
     * @param line the line number
     * @param column the column number
     * @return the builder for the next required property
     */
    ProblemBuilderDefiningCategory location(String path, Integer line, Integer column); // TODO rename to fileLocation

    // TODO discuss how to compose multiple explicit location information in problem builders

    // TODO think about use-case when plugin is applied using a class
    /**
     * Declares that this problem is emitted while applying a plugin.
     *
     * @param pluginId the ID of the applied plugin
     * @return the builder for the next required property
     * @since 8.5
     */
    ProblemBuilderDefiningCategory pluginLocation(String pluginId);

    /*
     * Declares that this problem has no associated location data.
     *
     * @return the builder for the next required property
     */
    ProblemBuilderDefiningCategory noLocation();
}
