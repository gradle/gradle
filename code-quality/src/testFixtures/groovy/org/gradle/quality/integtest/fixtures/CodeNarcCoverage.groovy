/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.JavaVersion
import org.gradle.api.plugins.quality.CodeNarcPlugin
import org.gradle.util.internal.VersionNumber

class CodeNarcCoverage {
    private static final List<String> ALL = [CodeNarcPlugin.DEFAULT_CODENARC_VERSION, "0.17", "0.21", "0.23", "0.24.1", "0.25.2", "1.0", "1.6.1"].asImmutable()

    private static final List<String> JDK11_SUPPORTED = versionsAboveInclusive(ALL, "0.23")
    private static final List<String> JDK14_SUPPORTED = JDK11_SUPPORTED - ['1.6.1', '1.0']

    static List<String> getSupportedVersionsByJdk() {
        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_14)) {
            return JDK14_SUPPORTED
        }
        return JavaVersion.current().java11Compatible ? JDK11_SUPPORTED : ALL
    }

    private static List<String> versionsAboveInclusive(List<String> versionsToFilter, String threshold) {
        versionsToFilter.findAll { VersionNumber.parse(it) >= VersionNumber.parse(threshold) }.asImmutable()
    }
}
