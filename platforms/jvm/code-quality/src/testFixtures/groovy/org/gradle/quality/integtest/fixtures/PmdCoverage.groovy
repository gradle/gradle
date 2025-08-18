/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.quality.integtest.fixtures

import org.gradle.api.plugins.quality.PmdPlugin
import org.gradle.internal.jvm.Jvm
import org.gradle.util.internal.VersionNumber

class PmdCoverage {

    /**
     * The java version and the first PMD version that supports it
     *
     * @see <a href="https://pmd.github.io/pmd/pmd_languages_java.html#overview-of-supported-java-language-versions">link</a>
     */
    private static final Map<Integer, String> VERSION_COVERAGE = [
        8: '5.1.0',
        9: '6.0.0',
        10: '6.4.0',
        11: '6.6.0',
        12: '6.13.0',
        13: '6.18.0',
        14: '6.22.0',
        15: '6.27.0',
        16: '6.32.0',
        17: '6.37.0',
        18: '6.44.0',
        19: '6.48.0',
        20: '6.55.0',
        21: '7.0.0',
        22: '7.0.0',
        23: '7.5.0',
        24: '7.10.0',
        25: '7.16.0',
    ]

    // See https://mvnrepository.com/artifact/net.sourceforge.pmd/pmd
    private static final String LATEST_PMD_VERSION = '7.16.0'

    private final static Set<String> TESTED_VERSIONS = [
        PmdPlugin.DEFAULT_PMD_VERSION,
        // LTS versions
        VERSION_COVERAGE[8],
        VERSION_COVERAGE[11],
        VERSION_COVERAGE[17],
        VERSION_COVERAGE[21],
        // Latest JVM that Gradle supports toolchains on
        VERSION_COVERAGE[25],
        LATEST_PMD_VERSION
    ]

    static Set<String> getSupportedVersionsByJdk() {
        int currentJvmVersion = Jvm.current().javaVersionMajor
        return TESTED_VERSIONS.findAll {
            supportsJdkVersion(VersionNumber.parse(it), currentJvmVersion)
        }
    }

    static boolean supportsJdkVersion(VersionNumber pmdVersion, int jvmVersion) {
        def supportedSince = VERSION_COVERAGE[jvmVersion]

        if (supportedSince == null) {
            return jvmVersion < 8
        }

        return pmdVersion >= VersionNumber.parse(supportedSince)
    }
}
