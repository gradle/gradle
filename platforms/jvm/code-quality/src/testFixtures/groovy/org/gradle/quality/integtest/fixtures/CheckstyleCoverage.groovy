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

package org.gradle.quality.integtest.fixtures

import org.gradle.api.JavaVersion
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.util.internal.VersionNumber

class CheckstyleCoverage {
    private final static List<String> JDK8_SUPPORTED = [CheckstylePlugin.DEFAULT_CHECKSTYLE_VERSION, '7.0', '7.6', '8.0', '8.12', '8.17', '8.24'].asImmutable()
    private final static List<String> JDK11_REQUIRED = ['10.3.3'].asImmutable()
    private final static List<String> ALL = JDK8_SUPPORTED + JDK11_REQUIRED

    static List<String> getSupportedVersionsByJdk() {
        JavaVersion.current() >= JavaVersion.VERSION_11 ? ALL : JDK8_SUPPORTED
    }

    static JavaVersion getMinimumSupportedJdkVersion(VersionNumber checkstyleVersion) {
        if (checkstyleVersion.getMajor() >= 10) {
            return JavaVersion.VERSION_11
        } else {
            return JavaVersion.VERSION_1_8
        }
    }
}
