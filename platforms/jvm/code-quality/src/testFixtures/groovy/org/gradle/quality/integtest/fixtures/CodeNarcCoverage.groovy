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
    /**
     * Covers the CodeNarc API differences the CodeNarc adapter handles:
     * file results without source code (before 2.0), file counts per directory (up to 3.0),
     * and the Groovy 3, 4 and 5 builds of CodeNarc.
     */
    public static final List<String> ALL = ["1.6.1", "2.0.0", "3.0.0", "3.7.0", CodeNarcPlugin.DEFAULT_CODENARC_VERSION, "4.0.0"].asImmutable()

    static List<String> getSupportedVersionsByCurrentJdk() {
        getSupportedVersionsByJdk(JavaVersion.current())
    }

    static List<String> getSupportedVersionsByJdk(JavaVersion version) {
        if (version.isJava11Compatible()) {
            return ALL
        }
        // CodeNarc 4 uses Groovy 5, which requires Java 11
        return ALL.findAll { !it.startsWith("4.") }
    }
}
