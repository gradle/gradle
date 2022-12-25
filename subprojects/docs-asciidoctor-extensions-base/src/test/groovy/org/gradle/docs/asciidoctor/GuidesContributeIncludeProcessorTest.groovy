/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.docs.asciidoctor

import org.asciidoctor.Asciidoctor
import org.asciidoctor.extension.JavaExtensionRegistry
import spock.lang.Specification
import spock.lang.Subject


@Subject(GuidesContributeIncludeProcessor)
class GuidesContributeIncludeProcessorTest extends Specification {
    Asciidoctor asciidoctor

    def setup() {
        asciidoctor = Asciidoctor.Factory.create()
        JavaExtensionRegistry extensionRegistry = asciidoctor.javaExtensionRegistry()
        extensionRegistry.includeProcessor(GuidesContributeIncludeProcessor.class)
    }

    def "defaults repo to gradle/guides and issue to blank url"() {
        given:
        String asciidocContent = """
            |= Doctitle
            |
            |include::contribute[]
            |""".stripMargin()
        when:
        String content = asciidoctor.convert(asciidocContent, [:])

        then:
        content.contains('Please <a href="https://github.com/gradle/guides/issues/new">add an issue</a> or pull request to <a href="https://github.com/gradle/guides">gradle/guides</a>')
    }

    def "defaults repo to gradle/guides and issue to blank url when repo-path attribute is defined"() {
        given:
        String asciidocContent = """
            |= Doctitle
            |
            |include::contribute[repo-path="gradle-guides/creating-build-scans"]
            |""".stripMargin()

        when:
        String content = asciidoctor.convert(asciidocContent, [:])

        then:
        content.contains('Please <a href="https://github.com/gradle/guides/issues/new">add an issue</a> or pull request to <a href="https://github.com/gradle/guides">gradle/guides</a>')
    }

    def "can use guide-name attribute on contribute block to customize the issue and repository urls"() {
        given:
        String asciidocContent = """
            |= Doctitle
            |
            |include::contribute[guide-name="creating-build-scans"]
            |""".stripMargin()

        when:
        String content = asciidoctor.convert(asciidocContent, [:])

        then:
        content.contains('Please <a href="https://github.com/gradle/guides/issues/new?labels=in:creating-build-scans">add an issue</a> or pull request to <a href="https://github.com/gradle/guides/tree/master/subprojects/creating-build-scans">gradle/guides</a>')
    }

    def "can use guide-name attribute on document to customize the issue and repository urls"() {
        given:
        String asciidocContent = """
            |:guide-name: creating-build-scans
            |= Doctitle
            |
            |include::contribute[]
            |""".stripMargin()

        when:
        String content = asciidoctor.convert(asciidocContent, [:])

        then:
        content.contains('Please <a href="https://github.com/gradle/guides/issues/new?labels=in:creating-build-scans">add an issue</a> or pull request to <a href="https://github.com/gradle/guides/tree/master/subprojects/creating-build-scans">gradle/guides</a>')
    }
}
