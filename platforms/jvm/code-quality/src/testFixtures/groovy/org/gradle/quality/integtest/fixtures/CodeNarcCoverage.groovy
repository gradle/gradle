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
    public static final List<String> ALL = [CodeNarcPlugin.DEFAULT_CODENARC_VERSION, "1.0", "1.6.1", "2.0.0", "2.2.0", "3.0.1"].asImmutable()

    private static boolean isAtLeastGroovy4() {
        return VersionNumber.parse(GroovySystem.version).major >= 4
    }

    private static final List<String> CURRENT_GROOVY_SUPPORTED = isAtLeastGroovy4() ? [CodeNarcPlugin.DEFAULT_CODENARC_VERSION].asImmutable()
                                                               : ALL

    private static final List<String> JDK11_SUPPORTED = versionsAboveInclusive(CURRENT_GROOVY_SUPPORTED, "0.23")
    private static final List<String> JDK14_SUPPORTED = JDK11_SUPPORTED - ['1.6.1', '1.0']

    static List<String> getSupportedVersionsByCurrentJdk() {
        getSupportedVersionsByJdk(JavaVersion.current())
    }

    static List<String> getSupportedVersionsByJdk(JavaVersion version) {
        if (version.isCompatibleWith(JavaVersion.VERSION_14)) {
            return JDK14_SUPPORTED
        } else if (version.isCompatibleWith(JavaVersion.VERSION_11)) {
            return JDK11_SUPPORTED
        } else {
            return CURRENT_GROOVY_SUPPORTED
        }
    }

    private static List<String> versionsAboveInclusive(List<String> versionsToFilter, String threshold) {
        versionsToFilter.findAll { VersionNumber.parse(it) >= VersionNumber.parse(threshold) }.asImmutable()
    }
}
