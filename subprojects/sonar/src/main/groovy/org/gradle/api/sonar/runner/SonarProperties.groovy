/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.sonar.runner

import org.gradle.api.Incubating

/**
 * The Sonar properties for the current Gradle project that are to be passed to the Sonar Runner.
 * The {@code properties} map is already populated with the defaults provided by Gradle, and can be
 * further manipulated as necessary. Before passing them on to the Sonar Runner, property values
 * are converted to Strings as follows:
 *
 * <ul>
 *     <li>{@Iterable}s are recursively converted and joined into a comma-separated String.</li>
 *     <li>All other values are converted to Strings by calling their {@code toString} method.</li>
 * </ul>
 */
@Incubating
class SonarProperties {
    /**
     * The Sonar properties for the current Gradle project that are to be passed to the Sonar runner.
     */
    Map<String, Object> properties = [:]

    /**
     * Convenience method for setting a single property.
     *
     * @param key the key of the property to be added
     * @param value the value of the property to be added
     */
    void property(String key, Object value) {
        properties[key] = value
    }

    /**
     * Convenience method for setting multiple properties.
     *
     * @param properties the properties to be added
     */
    void properties(Map<String, ?> properties) {
        this.properties.putAll(properties)
    }
}
