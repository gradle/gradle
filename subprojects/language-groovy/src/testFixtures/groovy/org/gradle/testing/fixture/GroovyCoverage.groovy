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
    private static final String[] PREVIOUS = ['1.5.8', '1.6.9', '1.7.11', '1.8.8', '2.0.5', '2.1.9', '2.2.2', '2.3.10', '2.4.15', '2.5.8', '3.0.12']
    private static final String[] FUTURE = ['4.0.4']

    static final Set<String> SUPPORTED_BY_JDK

    static final Set<String> SUPPORTS_GROOVYDOC
    static final Set<String> SUPPORTS_INDY
    static final Set<String> SUPPORTS_TIMESTAMP
    static final Set<String> SUPPORTS_PARAMETERS
    static final Set<String> SUPPORTS_DISABLING_AST_TRANSFORMATIONS
    static final Set<String> SINCE_3_0

    /**
     * The current Groovy version if stable, otherwise the latest stable version before the current version.
     */
    static final String CURRENT_STABLE

    static {
        SUPPORTED_BY_JDK = groovyVersionsSupportedByJdk(JavaVersion.current())
        SUPPORTS_GROOVYDOC = versionsAbove(SUPPORTED_BY_JDK, "1.6.9")
        // Indy compilation doesn't work in 2.2.2 and before
        SUPPORTS_INDY = versionsAbove(SUPPORTED_BY_JDK, "2.3.0")
        SUPPORTS_TIMESTAMP = versionsAbove(SUPPORTED_BY_JDK, "2.4.6")
        SUPPORTS_PARAMETERS = versionsAbove(SUPPORTED_BY_JDK, "2.5.0")
        SUPPORTS_DISABLING_AST_TRANSFORMATIONS = versionsAbove(SUPPORTED_BY_JDK, "2.0.0")
        SINCE_3_0 = versionsAbove(SUPPORTED_BY_JDK, "3.0.0")
        CURRENT_STABLE = isCurrentGroovyVersionStable()
            ? GroovySystem.version
            : versionsBelow(SUPPORTED_BY_JDK, GroovySystem.version).last()
    }

    static boolean supportsJavaVersion(String groovyVersion, JavaVersion javaVersion) {
        return groovyVersionsSupportedByJdk(javaVersion).contains(groovyVersion)
    }

    private static Set<String> groovyVersionsSupportedByJdk(JavaVersion javaVersion) {
        def allVersions = [*PREVIOUS]

        // Only test current Groovy version if it isn't a SNAPSHOT
        if (isCurrentGroovyVersionStable()) {
            allVersions += GroovySystem.version
        }

        allVersions.addAll(FUTURE)

        if (javaVersion.isCompatibleWith(JavaVersion.VERSION_15)) {
            return versionsAbove(allVersions, '3.0.0')
        } else if (javaVersion.isCompatibleWith(JavaVersion.VERSION_14)) {
            return versionsBetween(allVersions, '2.2.2', '2.5.10')
        } else {
            return allVersions
        }
    }

    private static boolean isCurrentGroovyVersionStable() {
        !GroovySystem.version.endsWith("-SNAPSHOT")
    }

    private static Set<String> versionsAbove(Collection<String> versionsToFilter, String threshold) {
        filterVersions(versionsToFilter, threshold, null)
    }

    private static Set<String> versionsBelow(Collection<String> versionsToFilter, String threshold) {
        filterVersions(versionsToFilter, null, threshold)
    }

    private static Set<String> versionsBetween(Collection<String> versionsToFilter, String lowerBound, String upperBound) {
        filterVersions(versionsToFilter, lowerBound, upperBound)
    }

    private static Set<String> filterVersions(Collection<String> versionsToFilter, @Nullable String lowerBound, @Nullable String upperBound) {
        versionsToFilter.findAll {
            def version = VersionNumber.parse(it)
            if (lowerBound != null && version < VersionNumber.parse(lowerBound)) {
                return false
            }
            if (upperBound != null && version > VersionNumber.parse(upperBound)) {
                return false
            }
            return true
        }.toSet().asImmutable()
    }
}
