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
import org.gradle.util.TestPrecondition
import org.gradle.util.VersionNumber

class CodeNarcCoverage {
    private final static List<String> ALL = ["0.17", "0.21", "0.23", "0.24.1", "0.25.2", "1.0", "1.2.1", CodeNarcPlugin.DEFAULT_CODENARC_VERSION].asImmutable()

    private final static List<String> JDK11_SUPPORTED = ALL.findAll { VersionNumber.parse(it) > VersionNumber.parse("0.21") }.asImmutable()
    // TODO: document this
    private final static List<String> JDK14_SUPPORTED = ALL - ['0.17', '0.21', '1.0', '1.2.1', '1.5']

    static List<String> getSupportedVersionsByJdk() {
        if (TestPrecondition.JDK13_OR_EARLIER.isFulfilled()) {
            return JavaVersion.current().java11Compatible ? JDK11_SUPPORTED : ALL
        }
        return JDK14_SUPPORTED
    }
}
