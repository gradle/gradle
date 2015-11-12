/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.model.java;

import org.gradle.api.Incubating;

/**
 * Describes the source language level.
 *
 * @since 2.10
 */
@Incubating
public enum JavaSourceLevel {

    VERSION_1_1("1.1"), VERSION_1_2("1.2"), VERSION_1_3("1.3"), VERSION_1_4("1.4"), VERSION_1_5("1.5"), VERSION_1_6("1.6"), VERSION_1_7("1.7"), VERSION_1_8("1.8"), VERSION_1_9("1.9");

    private final String version;

    private JavaSourceLevel(String version) {
        this.version = version;
    }

    /**
     * Returns the string representation of the source level in a [major_version].[minor_version] format.
     *
     * @return The string representation of the Java source level.
     */
    public String getVersion() {
        return version;
    }

    public static JavaSourceLevel from(String version) {
        for (JavaSourceLevel javaSourceLevel : JavaSourceLevel.values()) {
            if (javaSourceLevel.version.equals(version)) {
                return javaSourceLevel;
            }
        }
        throw new IllegalArgumentException(String.format("No JavaSourceLevel constant defined with version '%s'", version));
    }
}
