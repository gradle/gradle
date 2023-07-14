/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.fixtures

import org.gradle.api.JavaVersion

class ScalaCoverage {

    private static final Map<String, JavaVersion> SCALA_2_VERSIONS = [
        "2.11.12": JavaVersion.VERSION_1_9,
        "2.12.18": JavaVersion.VERSION_20,
        "2.13.11": JavaVersion.VERSION_20,
    ]
    private static final Map<String, JavaVersion> SCALA_3_VERSIONS = [
        "3.1.3": JavaVersion.VERSION_18,
        "3.2.2": JavaVersion.VERSION_19,
        "3.3.0": JavaVersion.VERSION_20,
    ]
    static final String[] SCALA_2 = SCALA_2_VERSIONS.keySet().toArray(new String[0])
    static final String[] SCALA_3 = SCALA_3_VERSIONS.keySet().toArray(new String[0])

    static final String[] DEFAULT = SCALA_2 + SCALA_3
    static final String[] LATEST_IN_MAJOR = [SCALA_2.last(), SCALA_3.last()]

    static JavaVersion getMaximumJavaVersionForScalaVersion(Object scalaVersion) {
        def v2 = SCALA_2_VERSIONS.get(scalaVersion)
        if (v2 != null) {
            return v2
        }
        def v3 = SCALA_3_VERSIONS.get(scalaVersion)
        if (v3 != null) {
            return v3
        }
        throw new IllegalArgumentException("Unknown Scala version: " + scalaVersion)
    }

}
