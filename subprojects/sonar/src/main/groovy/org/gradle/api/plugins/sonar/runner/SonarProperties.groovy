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
package org.gradle.api.plugins.sonar.runner

/**
 * The Sonar properties to be passed to the Sonar runner.
 * The {@code properties} map is already populated with
 * the defaults provided by Gradle, and can be manipulated
 * as necessary. Non-string values will be converted to
 * Strings as follows:
 *
 * <ul>
 *     <li>If the value is an {@Iterable}, its elements
 *     are recursively converted and joined into a comma-separated string.</li>
 *     <li>Otherwise, the value's {@code toString} method is called.</li>
 * </ul>
 */
class SonarProperties {
    Map<String, Object> properties

    void property(String key, Object value) {
        properties[key] = value
    }

    Object getProperty(String key) {
        properties[key]
    }

    void properties(Map<String, Object> properties) {
        this.properties.putAll(properties)
    }
}
