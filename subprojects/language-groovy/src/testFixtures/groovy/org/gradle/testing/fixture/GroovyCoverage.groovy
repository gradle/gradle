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

import javax.annotation.Nullable

class GroovyCoverage {
    private static final String[] PREVIOUS = ['1.5.8', '1.6.9',  '1.7.11', '1.8.8', '2.0.5', '2.1.9', '2.2.2', '2.3.10', '2.4.15', '2.5.8']
    private static final String[] FUTURE = ['4.0.0']

    static final List<String> SUPPORTED_BY_JDK

    static final List<String> SUPPORTS_GROOVYDOC
    static final List<String> SUPPORTS_TIMESTAMP
    static final List<String> SUPPORTS_PARAMETERS
    static final List<String> SUPPORTS_DISABLING_AST_TRANSFORMATIONS
    static final List<String> SINCE_3_0

    /**
     * The current Groovy version if stable, otherwise the latest stable version before the current version.
     */
    static final String CURRENT_STABLE

    static {
        SUPPORTED_BY_JDK = groovyVersionsSupportedByJdk(JavaVersion.current())
        SUPPORTS_GROOVYDOC = versionsAbove(SUPPORTED_BY_JDK, "1.6.9")
        SUPPORTS_TIMESTAMP = versionsAbove(SUPPORTED_BY_JDK, "2.4.6")
        SUPPORTS_PARAMETERS = versionsAbove(SUPPORTED_BY_JDK, "2.5.0")
        SUPPORTS_DISABLING_AST_TRANSFORMATIONS = versionsAbove(SUPPORTED_BY_JDK, "2.0.0")
        SINCE_3_0 = versionsAbove(SUPPORTED_BY_JDK, "3.0.0")
        CURRENT_STABLE = GroovySystem.version
    }

    static boolean supportsJavaVersion(String groovyVersion, JavaVersion javaVersion) {
        return groovyVersionsSupportedByJdk(javaVersion).contains(groovyVersion)
    }

    private static List<String> groovyVersionsSupportedByJdk(JavaVersion javaVersion) {
        def allVersions = [*PREVIOUS, GroovySystem.version, *FUTURE]

        checkNoVersionIsSkipped(allVersions)

        if (javaVersion.isCompatibleWith(JavaVersion.VERSION_16)) {
            return versionsAbove(allVersions, '3.0.0')
        } else if (javaVersion.isCompatibleWith(JavaVersion.VERSION_14)) {
            return versionsBetween(allVersions, '2.2.2', '2.5.10')
        } else {
            return allVersions
        }
    }

    private static void checkNoVersionIsSkipped(List<String> versions) {
        def toCheck = versions.collect { VersionNumber.parse(it) } as ArrayDeque
        def previous = toCheck.poll()
        while (true) {
            def current = toCheck.poll();
            if (current == null) {
                break;
            }
            switch (current.major - previous.major) {
                case 0:
                    assert current.minor - previous.minor <= 1, "Missing coverage for Groovy ${current.major}.${previous.minor + 1}"
                    break;
                case 1:
                    assert current.minor == 0, "Missing coverage for Groovy ${current.major}.0"
                    break;
                default:
                    assert false, "Missing coverage for Groovy ${current.major}.x"
                    break;
            }
            previous = current
        }
    }

    private static List<String> versionsAbove(List<String> versionsToFilter, String threshold) {
        filterVersions(versionsToFilter, threshold, null)
    }

    private static List<String> versionsBelow(List<String> versionsToFilter, String threshold) {
        filterVersions(versionsToFilter, null, threshold)
    }

    private static List<String> versionsBetween(List<String> versionsToFilter, String lowerBound, String upperBound) {
        filterVersions(versionsToFilter, lowerBound, upperBound)
    }

    private static List<String> filterVersions(List<String> versionsToFilter, @Nullable String lowerBound, @Nullable String upperBound) {
        versionsToFilter.findAll {
            def version = VersionNumber.parse(it)
            if (lowerBound != null && version < VersionNumber.parse(lowerBound)) {
                return false
            }
            if (upperBound != null && version > VersionNumber.parse(upperBound)) {
                return false
            }
            return true
        }.asImmutable()
    }
}
