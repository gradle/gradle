/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.language.assembler.plugins

import org.gradle.language.assembler.AssemblerSourceSet
import org.gradle.util.TestUtil
import spock.lang.Specification

class AssemblerLangPluginTest extends Specification {
    final def project = TestUtil.createRootProject()

    def "adds support for custom AssemblerSourceSets"() {
        when:
        project.plugins.apply(AssemblerLangPlugin)
        project.sources.create "test"

        then:
        project.sources.test.create("test_asm", AssemblerSourceSet) in AssemblerSourceSet
    }

    def "adds conventional AssemblerSourceSet"() {
        when:
        project.plugins.apply(AssemblerLangPlugin)
        project.sources.create "test"

        then:
        project.sources.test.asm in AssemblerSourceSet
    }
}
