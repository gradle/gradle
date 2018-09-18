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
import org.gradle.util.VersionNumber

class CheckstyleCoverage {
    // Note, this only work for major.minor versions
    private final static List<String> ALL = ['5.9', '6.2', '6.9', '6.12', '7.0', '7.6', CheckstylePlugin.DEFAULT_CHECKSTYLE_VERSION].asImmutable()

    private final static List<VersionNumber> ALL_VERSIONS = ALL.collect { VersionNumber.parse(it) }

    // JDK7 support was dropped in 7.0
    private final static List<String> JDK7_SUPPORTED = ALL_VERSIONS.findAll({ it < VersionNumber.parse("7.0") }).collect({ "${it.major}.${it.minor}" }).asImmutable()

    static List<String> getSupportedVersionsByJdk() {
        JavaVersion.current().java7 ? JDK7_SUPPORTED : ALL
    }
}
