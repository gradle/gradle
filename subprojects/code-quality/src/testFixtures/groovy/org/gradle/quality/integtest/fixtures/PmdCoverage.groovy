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

class PmdCoverage {
    private final static List<String> ALL = [PmdPlugin.DEFAULT_PMD_VERSION, '4.3', '5.0.5', '5.1.1', '5.3.3', '6.0.0' /*Java 9*/, '6.13.0' /*Java 12*/].asImmutable()

    static List<String> getSupportedVersionsByJdk() {
        ALL
    }
}
