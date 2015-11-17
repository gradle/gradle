/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.base

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

@Requires(TestPrecondition.ONLINE)
class LanguageTypeSampleIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    Sample languageTypeSample = new Sample(temporaryFolder, "customModel/languageType")

    def "shows custom language sourcesets in component"() {
        given:
        sample languageTypeSample
        when:
        succeeds "components"
        then:
        output.contains """
DocumentationComponent 'docs'
-----------------------------

Source sets
    DefaultMarkdownSourceSet 'docs:userguide'
        srcDir: src${File.separator}docs${File.separator}userguide

Binaries
    DefaultDocumentationBinary 'docs:binary'
        build using task: :docsBinary
"""
    }

    def "can build binary"() {
        given:
        sample languageTypeSample
        when:
        succeeds "assemble"
        then:
        executedTasks == [":compileDocsBinaryUserguide", ":zipDocsBinary", ":docsBinary", ":assemble"]
        and:
        new ZipTestFixture(languageTypeSample.dir.file("build/binary/binary.zip")).containsDescendants(
                "userguide/chapter1.html",
                "userguide/chapter2.html",
                "userguide/index.html")

    }
}
