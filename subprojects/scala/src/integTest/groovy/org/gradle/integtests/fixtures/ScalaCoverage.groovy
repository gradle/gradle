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
import org.gradle.test.fixtures.VersionCoverage

class ScalaCoverage {

    static final String LATEST_SCALA_2 = "2.13.11"
    private static final Set<String> SCALA_2 = [
        "2.11.12",
        "2.12.18",
    ] + [LATEST_SCALA_2]
    private static final String LATEST_SCALA_3 = "3.3.0"
    private static final Set<String> SCALA_3 = [
        "3.1.3",
        "3.2.2",
    ] + [LATEST_SCALA_3]


    static final Set<String> SUPPORTED_BY_JDK = scalaVersionsSupportedByJdk(JavaVersion.current())
    static final Set<String> LATEST_IN_MAJOR = SUPPORTED_BY_JDK.findAll {
        it == LATEST_SCALA_2 || it == LATEST_SCALA_3
    }

    static Set<String> scalaVersionsSupportedByJdk(JavaVersion javaVersion) {
        return scala2VersionsSupportedByJdk(javaVersion) + scala3VersionsSupportedByJdk(javaVersion)
    }

    private static Set<String> scala2VersionsSupportedByJdk(JavaVersion javaVersion) {
        if (javaVersion.isCompatibleWith(JavaVersion.VERSION_1_9)) {
            return VersionCoverage.versionsAtLeast(SCALA_2, "2.13.11")
        }
        if (javaVersion.isCompatibleWith(JavaVersion.VERSION_1_8)) {
            return VersionCoverage.versionsBetweenExclusive(SCALA_2, "2.11.12", "2.12.0") +
                VersionCoverage.versionsAtMost(SCALA_2, "2.12.18")
        }
        return SCALA_2
    }

    private static Set<String> scala3VersionsSupportedByJdk(JavaVersion javaVersion) {
        if (javaVersion.isCompatibleWith(JavaVersion.VERSION_20)) {
            return VersionCoverage.versionsAtLeast(SCALA_3, "3.3.0")
        }
        if (javaVersion.isCompatibleWith(JavaVersion.VERSION_19)) {
            return VersionCoverage.versionsAtLeast(SCALA_3, "3.2.2")
        }
        if (javaVersion.isCompatibleWith(JavaVersion.VERSION_18)) {
            return VersionCoverage.versionsAtLeast(SCALA_3, "3.1.3")
        }
        return SCALA_3
    }

}
