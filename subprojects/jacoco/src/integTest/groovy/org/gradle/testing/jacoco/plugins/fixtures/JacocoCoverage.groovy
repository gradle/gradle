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

package org.gradle.testing.jacoco.plugins.fixtures

import org.gradle.api.JavaVersion
import org.gradle.testing.jacoco.plugins.JacocoPlugin

final class JacocoCoverage {

    JacocoCoverage() {}

    final static String[] ALL = ['0.6.0.201210061924', '0.6.2.201302030002', '0.6.3.201306030806', '0.7.1.201405082137', '0.7.6.201602180812', JacocoPlugin.DEFAULT_JACOCO_VERSION].asImmutable()

    final static List<String> COVERAGE_CHECK_SUPPORTED = filter(JacocoVersion.CHECK_INTRODUCED)

    final static List<String> COVERAGE_CHECK_UNSUPPORTED = ALL.findAll {
        def jacocoVersion = new JacocoVersion(it)
        def supportedJacocoVersion = JacocoVersion.CHECK_INTRODUCED
        jacocoVersion.compareTo(supportedJacocoVersion) == -1
    }.asImmutable()

    final static List<String> SUPPORTS_JDK_8_OR_HIGHER = filter(JacocoVersion.SUPPORTS_JDK_8)
    final static List<String> SUPPORTS_JDK_9_OR_HIGHER = filter(JacocoVersion.SUPPORTS_JDK_9)

    final static List<String> DEFAULT_COVERAGE = JavaVersion.current().isJava9Compatible() ? SUPPORTS_JDK_9_OR_HIGHER : SUPPORTS_JDK_8_OR_HIGHER

    static List<String> filter(JacocoVersion threshold) {
        ALL.findAll { new JacocoVersion(it) >= threshold }
    }

    private static class JacocoVersion implements Comparable<JacocoVersion> {
        final static CHECK_INTRODUCED = new JacocoVersion(0, 6, 3)
        final static SUPPORTS_JDK_8 = new JacocoVersion(0, 7, 0)
        final static SUPPORTS_JDK_9 = new JacocoVersion(0, 7, 8)
        private final Integer major
        private final Integer minor
        private final Integer patch

        JacocoVersion(String version) {
            def versionParts = version.split('\\.')
            major = versionParts[0].toInteger()
            minor = versionParts[1].toInteger()
            patch = versionParts[2].toInteger()
        }

        JacocoVersion(Integer major, Integer minor, Integer patch) {
            this.major = major
            this.minor = minor
            this.patch = patch
        }

        @Override
        int compareTo(JacocoVersion o) {
            if (major > o.major) {
                return 1
            }
            if (major < o.major) {
                return -1
            }

            if (minor > o.minor) {
                return 1
            }
            if (minor < o.minor) {
                return -1
            }

            if (patch > o.patch) {
                return 1
            }
            if (patch < o.patch) {
                return -1
            }

            0
        }
    }
}
