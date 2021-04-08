/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testing.fixture

import org.gradle.api.JavaVersion
import org.gradle.util.internal.VersionNumber

class GroovyCoverage {
    private static final String[] PREVIOUS = ['1.5.8', '1.6.9', '1.7.11', '1.8.8', '2.0.5', '2.1.9', '2.2.2', '2.3.10', '2.4.15', '2.5.8']

    static final List<String> SUPPORTED_BY_JDK

    static final List<String> SUPPORTS_GROOVYDOC
    static final List<String> SUPPORTS_TIMESTAMP
    static final List<String> SUPPORTS_PARAMETERS

    static {
        SUPPORTED_BY_JDK = groovyVersionsSupportedByJdk(JavaVersion.current())
        SUPPORTS_GROOVYDOC = versionsAbove(SUPPORTED_BY_JDK, "1.6.9")
        SUPPORTS_TIMESTAMP = versionsAbove(SUPPORTED_BY_JDK, "2.4.6")
        SUPPORTS_PARAMETERS = versionsAbove(SUPPORTED_BY_JDK, "2.5.0")
    }

    static boolean supportsJavaVersion(String groovyVersion, JavaVersion javaVersion) {
        return groovyVersionsSupportedByJdk(javaVersion).contains(groovyVersion)
    }

    private static List<String> groovyVersionsSupportedByJdk(JavaVersion javaVersion) {
        def allVersions = [*PREVIOUS]

        // Only test current Groovy version if it isn't a SNAPSHOT
        if (!GroovySystem.version.endsWith("-SNAPSHOT")) {
            allVersions += GroovySystem.version
        }

        if (javaVersion.isCompatibleWith(JavaVersion.VERSION_16)) {
            return versionsAbove(allVersions, '3.0.0')
        } else if (javaVersion.isCompatibleWith(JavaVersion.VERSION_14)) {
            return versionsBetween(allVersions, '2.2.2', '2.5.10')
        } else {
            return allVersions
        }
    }

    private static List<String> versionsAbove(List<String> versionsToFilter, String threshold) {
        versionsToFilter.findAll { VersionNumber.parse(it) >= VersionNumber.parse(threshold) }.asImmutable()
    }

    private static List<String> versionsBetween(List<String> versionsToFilter, String lowerBound, String upperBound) {
        versionsToFilter.findAll {
            def version = VersionNumber.parse(it)
            version <= VersionNumber.parse(lowerBound) || version >= VersionNumber.parse(upperBound)
        }.asImmutable()
    }
}
