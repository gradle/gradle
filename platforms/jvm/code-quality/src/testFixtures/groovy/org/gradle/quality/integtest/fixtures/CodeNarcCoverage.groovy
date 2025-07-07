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

class CodeNarcCoverage {
    public static final List<String> ALL = [CodeNarcPlugin.DEFAULT_CODENARC_VERSION].asImmutable()

    static List<String> getSupportedVersionsByCurrentJdk() {
        getSupportedVersionsByJdk(JavaVersion.current())
    }

    // Leaving this in since it might change in the future.
    static List<String> getSupportedVersionsByJdk(JavaVersion version) {
        return ALL
    }
}
