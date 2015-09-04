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

package org.gradle.api.tasks.diagnostics.internal.text

import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.SystemProperties
import org.gradle.logging.TestStyledTextOutput
import org.gradle.reporting.ReportRenderer
import org.gradle.util.TextUtil
import spock.lang.Specification

class DefaultTextReportBuilderTest extends Specification {
    def output = new TestStyledTextOutput()
    def fileResolver = Stub(FileResolver)
    def builder = new DefaultTextReportBuilder(output, fileResolver)
    def eol = SystemProperties.instance.getLineSeparator()

    def "formats heading"() {
        given:
        builder.heading("heading")

        expect:
        output.value == """
{header}------------------------------------------------------------
heading
------------------------------------------------------------{normal}

"""
    }

    def "formats subheading"() {
        given:
        builder.subheading("heading")

        expect:
        output.value == """{header}heading
-------{normal}
"""
    }

    def "formats collection"() {
        def renderer = Mock(ReportRenderer)

        given:
        renderer.render(_, _) >> { Number number, TextReportBuilder builder ->
            builder.output.println("[$number]")
        }

        when:
        builder.collection("Things", [1, 2], renderer, "things")

        then:
        output.value == """Things
    [1]
    [2]
"""
    }

    def "formats empty collection"() {
        def renderer = Mock(ReportRenderer)

        when:
        builder.collection("Things", [], renderer, "things")

        then:
        output.value == """Things
    No things.
"""
    }

    def "formats item"() {
        when:
        builder.item("some value")

        then:
        output.value == """    some value
"""
    }

    def "formats item with file value"() {
        def file = new File("thing")
        fileResolver.resolveAsRelativePath(file) >> "path/thing"

        when:
        builder.item(file)

        then:
        output.value == """    path/thing
"""
    }

    def "formats item with title"() {
        when:
        builder.item("the title", "the value")

        then:
        output.value == """    the title: the value
"""
    }

    def "formats item with title and file value"() {
        def file = new File("thing")
        fileResolver.resolveAsRelativePath(file) >> "path/thing"

        when:
        builder.item("the title", file)

        then:
        output.value == """    the title: path/thing
"""
    }

    def "formats multiline item" () {
        when:
        builder.item("first line${eol}  second line${eol}  third line")

        then:
        outputMatches """    first line
      second line
      third line
"""
    }

    def "formats multiline item with title" () {
        when:
        builder.item("the title", "first line${eol}  second line${eol}  third line")

        then:
        outputMatches """    the title: first line
      second line
      third line
"""
    }

    void outputMatches(String text) {
        assert TextUtil.normaliseLineSeparators(output.value) == TextUtil.normaliseLineSeparators(text)
    }
}
