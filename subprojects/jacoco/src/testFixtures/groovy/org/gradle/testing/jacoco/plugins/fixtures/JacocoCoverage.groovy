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

    private JacocoCoverage() {}

    private static final String[] ALL = [JacocoPlugin.DEFAULT_JACOCO_VERSION, '0.7.1.201405082137', '0.7.6.201602180812', '0.8.3'].asImmutable()

    static List<String> getSupportedVersionsByJdk() {
        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_14)) {
            return filter(JacocoVersion.SUPPORTS_JDK_14)
        } else if (JavaVersion.current().isJava9Compatible()) {
            return filter(JacocoVersion.SUPPORTS_JDK_9)
        }
        return filter(JacocoVersion.SUPPORTS_JDK_8)
    }

    private static List<String> filter(JacocoVersion threshold) {
        ALL.findAll { new JacocoVersion(it) >= threshold }.asImmutable()
    }

    private static class JacocoVersion implements Comparable<JacocoVersion> {
        static final SUPPORTS_JDK_8 = new JacocoVersion(0, 7, 0)
        static final SUPPORTS_JDK_9 = new JacocoVersion(0, 7, 8)
        static final SUPPORTS_JDK_14 = new JacocoVersion(0, 8, 5)

        private final int major
        private final int minor
        private final int patch

        JacocoVersion(String version) {
            def versionParts = version.split('\\.')
            major = versionParts[0].toInteger()
            minor = versionParts[1].toInteger()
            patch = versionParts[2].toInteger()
        }

        JacocoVersion(int major, int minor, int patch) {
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
