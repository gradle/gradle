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
import org.gradle.internal.logging.text.TestStyledTextOutput
import org.gradle.reporting.ReportRenderer
import org.gradle.util.internal.TextUtil
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

    def "formats top-level item with structured value"() {
        def renderer = Mock(ReportRenderer)

        given:
        renderer.render(_, _) >> { String value, TextReportBuilder builder ->
            builder.heading("Thing")
            builder.item("value", value)
            builder.item("length", value.length() as String)
        }

        when:
        builder.heading("heading")
        builder.item("some thing", renderer)

        then:
        output.value == """
{header}------------------------------------------------------------
heading
------------------------------------------------------------{normal}

{header}Thing
-----{normal}
value: some thing
length: 10
"""
    }

    def "formats top-level collection of simple values"() {
        def renderer = Mock(ReportRenderer)

        given:
        renderer.render(_, _) >> { Number number, TextReportBuilder builder ->
            builder.output.println("[$number]")
        }

        when:
        builder.heading("heading")
        builder.collection("Things", [1, 2], renderer, "things")

        then:
        output.value == """
{header}------------------------------------------------------------
heading
------------------------------------------------------------{normal}

{header}Things
------{normal}
[1]
[2]
"""
    }

    def "formats top-level collection of structured values"() {
        def renderer = Mock(ReportRenderer)

        given:
        renderer.render(_, _) >> { Number number, TextReportBuilder builder ->
            builder.heading("Item $number")
            builder.item("value", number as String)
        }

        when:
        builder.heading("heading")
        builder.collection("Things", [1, 2], renderer, "things")

        then:
        output.value == """
{header}------------------------------------------------------------
heading
------------------------------------------------------------{normal}

{header}Things
------{normal}
Item 1
    value: 1
Item 2
    value: 2
"""
    }

    def "formats second-level collection of structured values"() {
        def renderer = Mock(ReportRenderer)
        def itemRenderer = Mock(ReportRenderer)

        given:
        renderer.render(_, _) >> { List<Number> values, TextReportBuilder builder ->
            builder.heading("Thing")
            builder.collection("Values", values, itemRenderer, "things")
        }
        itemRenderer.render(_, _) >> { Number number, TextReportBuilder builder ->
            builder.heading("Item $number")
            builder.item("value", number as String)
        }

        when:
        builder.heading("heading")
        builder.item([1, 2], renderer)

        then:
        output.value == """
{header}------------------------------------------------------------
heading
------------------------------------------------------------{normal}

{header}Thing
-----{normal}
Values
    Item 1
        value: 1
    Item 2
        value: 2
"""
    }

    def "formats top-level collection of collections"() {
        def renderer = Mock(ReportRenderer)
        def nestedRenderer = Mock(ReportRenderer)

        given:
        renderer.render(_, _) >> { Number number, TextReportBuilder builder ->
            builder.heading("Item $number")
            builder.collection("values", [number as String], nestedRenderer, "values")
        }
        nestedRenderer.render(_, _) >> { String value, TextReportBuilder builder ->
            builder.heading("Item")
            builder.item("value", value)
        }

        when:
        builder.heading("heading")
        builder.collection("Things", [1, 2], renderer, "things")

        then:
        output.value == """
{header}------------------------------------------------------------
heading
------------------------------------------------------------{normal}

{header}Things
------{normal}
Item 1
    values:
        Item
            value: 1
Item 2
    values:
        Item
            value: 2
"""
    }

    def "formats top-level empty collection"() {
        def renderer = Mock(ReportRenderer)

        when:
        builder.heading("heading")
        builder.collection("Things", [], renderer, "things")

        then:
        output.value == """
{header}------------------------------------------------------------
heading
------------------------------------------------------------{normal}

{header}Things
------{normal}
    No things.
"""
    }

    def "formats top-level collection following another item"() {
        def renderer = Mock(ReportRenderer)

        given:
        renderer.render(_, _) >> { Number number, TextReportBuilder builder ->
            builder.heading("Item $number")
            builder.item("value", number as String)
        }

        when:
        builder.heading("heading")
        builder.item("Item", "some value")
        builder.collection("Things", [1, 2], renderer, "things")

        then:
        output.value == """
{header}------------------------------------------------------------
heading
------------------------------------------------------------{normal}

Item: some value
Things:
    Item 1
        value: 1
    Item 2
        value: 2
"""
    }

    def "formats item with string value"() {
        when:
        builder.item("some value")

        then:
        output.value == """some value
"""
    }

    def "formats item with file value"() {
        def file = new File("thing")
        fileResolver.resolveForDisplay(file) >> "path/thing"

        when:
        builder.item(file)

        then:
        output.value == """path/thing
"""
    }

    def "formats item with title and string value"() {
        when:
        builder.item("the title", "the value")

        then:
        output.value == """the title: the value
"""
    }

    def "formats item with title and file value"() {
        def file = new File("thing")
        fileResolver.resolveForDisplay(file) >> "path/thing"

        when:
        builder.item("the title", file)

        then:
        output.value == """the title: path/thing
"""
    }

    def "formats item with title and iterable value"() {
        when:
        builder.item("the title", ["the value", "the value 2"])

        then:
        output.value == """the title: the value, the value 2
"""
    }

    def "formats item with multiline string value" () {
        when:
        builder.item("first line${eol}  second line${eol}  third line")

        then:
        outputMatches """first line
  second line
  third line
"""
    }

    def "formats item with title and multiline string value" () {
        when:
        builder.item("the title", "first line${eol}  second line${eol}  third line")

        then:
        outputMatches """the title: first line
      second line
      third line
"""
    }

    void outputMatches(String text) {
        assert TextUtil.normaliseLineSeparators(output.value) == TextUtil.normaliseLineSeparators(text)
    }
}
