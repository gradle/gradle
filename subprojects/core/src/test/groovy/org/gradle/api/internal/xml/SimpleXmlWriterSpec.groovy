/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.xml

import spock.lang.Specification

import javax.xml.parsers.DocumentBuilderFactory

/**
 * by Szczepan Faber, created at: 12/3/12
 */
class SimpleXmlWriterSpec extends Specification {

    private sw = new ByteArrayOutputStream()
    private writer = new SimpleXmlWriter(sw)

    String getXml() {
        def text = sw.toString("UTF-8")
        println "TEXT {$text}"
        def document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(sw.toByteArray()))
        assert document
        return text
    }

    def "writes basic xml"() {
        when:
        writer.writeStartElement("root").attribute("items", "9")
        writer.writeEmptyElement("item")
        writer.writeStartElement("item").attribute("size", "10m")
        writer.writeCharacters("some chars")
        writer.writeStartElement("foo")
        writer.writeEndElement()
        writer.writeEndElement()
        writer.writeEndElement()

        then:
        xml == '<?xml version="1.0" encoding="UTF-8"?><root items="9"><item/><item size="10m">some chars<foo></foo></item></root>'
    }

    def "encodes for xml"() {
        when:
        writer.writeStartElement("root")
        writer.writeStartElement("item").attribute("size", "encoded: &lt; < > ' \"")
        writer.writeCharacters("chars with interesting stuff: &lt; < > ' \" ]]>")
        writer.writeEndElement()
        writer.writeEndElement()

        then:
        xml.contains('<item size="encoded: &amp;lt; &lt; &gt; \' &quot;">chars with interesting stuff: &amp;lt; &lt; &gt; \' &quot; ]]&gt;</item>')
    }

    def "encodes non-ascii characters"() {
        when:
        writer.writeStartElement("\u0200").attribute("\u0201", "\u0202")
        writer.writeCharacters("\u0203")
        writer.writeEndElement()

        then:
        xml.contains('<\u0200 \u0201="\u0202">\u0203</\u0200>')
    }

    def "writes CDATA"() {
        when:
        writer.writeStartElement("root")
        writer.writeStartElement("stuff")

        writer.writeStartCDATA()
        writer.writeCDATA('x hey x'.toCharArray(), 2, 6)
        writer.writeCDATA('joe'.toCharArray())
        writer.writeEndCDATA()

        writer.writeEndElement()

        writer.writeStartCDATA()
        writer.writeCDATA('encodes: ]]> '.toCharArray())
        writer.writeCDATA('does not encode: ]] '.toCharArray())      
        writer.writeCDATA('html allowed: <> &amp;'.toCharArray())
        writer.writeEndCDATA()

        writer.writeEndElement()

        then:
        xml.contains('<stuff><![CDATA[hey joe]]></stuff><![CDATA[encodes: ]]]]><![CDATA[> does not encode: ]] html allowed: <> &amp;]]>')
    }

    def "encodes CDATA when token on the border"() {
        when:
        //the end token is on the border of both char arrays
        writer.writeStartElement('root')
        writer.writeStartCDATA()
        writer.writeCDATA('stuff ]]'.toCharArray())
        writer.writeCDATA('> more stuff'.toCharArray())
        writer.writeEndCDATA()
        writer.writeEndElement()

        then:
        xml.contains('<![CDATA[stuff ]]]]><![CDATA[> more stuff]]>')
    }

    def "does not encode CDATA when token separated in different CDATAs"() {
        when:
        //the end token is on the border of both char arrays

        writer.writeStartElement('root')

        writer.writeStartCDATA();
        writer.writeCDATA('stuff ]]'.toCharArray())
        writer.writeEndCDATA();

        writer.writeStartCDATA()
        writer.writeCDATA('> more stuff'.toCharArray())
        writer.writeEndCDATA();

        writer.writeEndElement()

        then:
        xml.contains('<root><![CDATA[stuff ]]]]><![CDATA[> more stuff]]></root>')
    }

    def "has basic stack validation"() {
        writer.writeStartElement("root")
        writer.writeEndElement()

        when:
        writer.writeEndElement()

        then:
        thrown(IllegalStateException)
    }

    def "closes tags"() {
        when:
        writer.writeStartElement("root")
        action.call(writer)
        writer.writeEndElement()

        then:
        sw.toString().contains("<root>") //is closed with '>'

        where:
        action << [{it.writeStartElement("foo"); it.writeEndElement()},
                    {it.writeStartCDATA()},
                    {it.writeCharacters("bar")},
                    {},
                    {it.writeEmptyElement("baz")}]
    }

    def "closes attributed tags"() {
        when:
        writer.writeStartElement("root")
        writer.attribute("foo", '115')
        action.call(writer)
        writer.writeEndElement()

        then:
        sw.toString().contains('<root foo="115">') //is closed with '>'

        where:
        action << [{it.writeStartElement("foo"); it.writeEndElement()},
                    {it.writeStartCDATA()},
                    {it.writeCharacters("bar")},
                    {},
                    {it.writeEmptyElement("baz")}]
    }

    def "allows valid tag names"() {
        when:
        writer.writeStartElement(name)

        then:
        notThrown(IllegalArgumentException)

        where:
        name << ["name", "NAME", "with-dashes", "with.dots", "with123digits", ":", "_", "\u037f\u0300"]
    }

    def "validates tag names"() {
        when:
        writer.writeStartElement(name)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "Invalid element name: '$name'"

        where:
        name << ["tag with space", "", "-invalid-start-char", "  ", "912", "\u00d7"]
    }

    def "allows valid attribute names"() {
        when:
        writer.writeStartElement("foo").attribute(name, "foo")

        then:
        notThrown(IllegalArgumentException)

        where:
        name << ["name", "NAME", "with-dashes", "with.dots", "with123digits", ":", "_", "\u037f\u0300"]
    }

    def "validates attribute names"() {
        when:
        writer.writeStartElement("foo").attribute(name, "foo")

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "Invalid attribute name: '$name'"

        where:
        name << ["attribute with space", "", "-invalid-start-char", "  ", "912", "\u00d7"]
    }
}