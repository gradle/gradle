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
import org.gradle.test.fixtures.VersionCoverage
import org.gradle.util.internal.VersionNumber

class GroovyCoverage {
    private static final String[] PREVIOUS = ['1.5.8', '1.6.9', '1.7.11', '1.8.8', '2.0.5', '2.1.9', '2.2.2', '2.3.10', '2.4.15', '2.5.8', '3.0.22']
    private static final String[] FUTURE = ['4.0.22']

    static final Set<String> SUPPORTED_BY_JDK

    static final Set<String> SUPPORTS_GROOVYDOC
    static final Set<String> SUPPORTS_INDY
    static final Set<String> SUPPORTS_TIMESTAMP
    static final Set<String> SUPPORTS_PARAMETERS
    static final Set<String> SUPPORTS_DISABLING_AST_TRANSFORMATIONS
    static final Set<String> SINCE_3_0
    static final Set<String> SINCE_4_0

    /**
     * The lowest working Groovy 3 version for the current JDK.
     */
    static final String MINIMAL_GROOVY_3

    /**
     * The current Groovy version if stable, otherwise the latest stable version before the current version.
     */
    static final String CURRENT_STABLE

    static {
        SUPPORTED_BY_JDK = groovyVersionsSupportedByJdk(JavaVersion.current())
        SUPPORTS_GROOVYDOC = VersionCoverage.versionsAtLeast(SUPPORTED_BY_JDK, "1.6.9")
        // Indy compilation doesn't work in 2.2.2 and before
        SUPPORTS_INDY = VersionCoverage.versionsAtLeast(SUPPORTED_BY_JDK, "2.3.0")
        SUPPORTS_TIMESTAMP = VersionCoverage.versionsAtLeast(SUPPORTED_BY_JDK, "2.4.6")
        SUPPORTS_PARAMETERS = VersionCoverage.versionsAtLeast(SUPPORTED_BY_JDK, "2.5.0")
        SUPPORTS_DISABLING_AST_TRANSFORMATIONS = VersionCoverage.versionsAtLeast(SUPPORTED_BY_JDK, "2.0.0")
        SINCE_3_0 = VersionCoverage.versionsAtLeast(SUPPORTED_BY_JDK, "3.0.0")
        SINCE_4_0 = VersionCoverage.versionsAtLeast(SUPPORTED_BY_JDK, "4.0.0")
        CURRENT_STABLE = isCurrentGroovyVersionStable()
            ? GroovySystem.version
            : VersionCoverage.versionsAtMost(SUPPORTED_BY_JDK, GroovySystem.version).last()
        MINIMAL_GROOVY_3 = VersionCoverage.versionsBelow(SINCE_3_0, "4.0.0").first()
    }

    static boolean supportsJavaVersion(String groovyVersion, JavaVersion javaVersion) {
        return groovyVersionsSupportedByJdk(javaVersion).contains(groovyVersion)
    }

    /**
     * Computes the Java version that corresponds to the Java bytecode version actually produced by the Groovy compiler.
     */
    static JavaVersion getEffectiveTarget(VersionNumber groovyVersion, JavaVersion target) {
        if (groovyVersion.major == 4) {
            return target
        } else if (groovyVersion.major == 3) {
            // If Groovy 3 does not support the requested target version, it silently falls back to an internal default
            return JavaVersion.VERSION_17.isCompatibleWith(target) ? target : JavaVersion.VERSION_1_8
        }
        throw new IllegalArgumentException("Computing effective target for Groovy version $groovyVersion is not supported")
    }

    private static Set<String> groovyVersionsSupportedByJdk(JavaVersion javaVersion) {
        def allVersions = [*PREVIOUS]

        // Only test current Groovy version if it isn't a SNAPSHOT
        if (isCurrentGroovyVersionStable()) {
            allVersions += GroovySystem.version
        }

        allVersions.addAll(FUTURE)

        if (javaVersion.isCompatibleWith(JavaVersion.VERSION_23)) {
            return VersionCoverage.versionsAtLeast(allVersions, '3.0.22')
        } else if (javaVersion.isCompatibleWith(JavaVersion.VERSION_22)) {
            return VersionCoverage.versionsAbove(allVersions, '3.0.20')
        } else if (javaVersion.isCompatibleWith(JavaVersion.VERSION_20)) {
            return VersionCoverage.versionsAbove(allVersions, '3.0.13')
        } else if (javaVersion.isCompatibleWith(JavaVersion.VERSION_15)) {
            return VersionCoverage.versionsAtLeast(allVersions, '3.0.0')
        } else if (javaVersion.isCompatibleWith(JavaVersion.VERSION_14)) {
            return VersionCoverage.versionsBetweenInclusive(allVersions, '2.2.2', '2.5.10')
        } else {
            return allVersions
        }
    }

    private static boolean isCurrentGroovyVersionStable() {
        !GroovySystem.version.endsWith("-SNAPSHOT")
    }
}
