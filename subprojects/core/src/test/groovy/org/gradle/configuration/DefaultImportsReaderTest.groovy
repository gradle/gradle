/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.configuration

import org.gradle.util.internal.Resources
import org.junit.Rule
import spock.lang.Specification

class DefaultImportsReaderTest extends Specification {
    @Rule
    public Resources resources = new Resources()
    DefaultImportsReader reader = new DefaultImportsReader()

    def "default import packages contain org.gradle.api"() {
        expect:
        reader.importPackages.contains('org.gradle.api')
    }

    def "fq default imports do not contain package private types"() {
        expect:
        null == reader.simpleNameToFullClassNamesMapping
            .collectMany { it.value }
            .find { it == "org.gradle.plugin.devel.plugins.MavenPluginPublishPlugin" }

    }
}
