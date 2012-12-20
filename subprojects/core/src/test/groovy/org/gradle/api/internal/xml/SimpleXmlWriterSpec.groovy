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
        writer.writeStartElement("item").writeEndElement()
        writer.writeStartElement("item").attribute("size", "10m")
        writer.writeCharacters("some chars")
        writer.writeCharacters(" and some other".toCharArray())
        writer.writeCharacters("x  chars.x".toCharArray(), 2, 7)
        writer.writeStartElement("foo").writeCharacters(" ")
        writer.writeEndElement()
        writer.writeEndElement()
        writer.writeEndElement()

        then:
        xml == '<?xml version="1.0" encoding="UTF-8"?><root items="9"><item/><item size="10m">some chars and some other chars.<foo> </foo></item></root>'
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
        writer.writeCharacters('x hey x'.toCharArray(), 2, 4)
        writer.writeCharacters('joe'.toCharArray())
        writer.writeCharacters("!")
        writer.writeEndCDATA()

        writer.writeEndElement()

        writer.writeStartCDATA()
        writer.writeCharacters('encodes: ]]> ')
        writer.writeCharacters('does not encode: ]] ')
        writer.writeCharacters('html allowed: <> &amp;')
        writer.writeEndCDATA()

        writer.writeEndElement()

        then:
        xml.contains('<stuff><![CDATA[hey joe!]]></stuff><![CDATA[encodes: ]]]]><![CDATA[> does not encode: ]] html allowed: <> &amp;]]>')
    }

    def "encodes CDATA when token on the border"() {
        when:
        //the end token is on the border of both char arrays
        writer.writeStartElement('root')
        writer.writeStartCDATA()
        writer.writeCharacters('stuff ]]')
        writer.writeCharacters('> more stuff')
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
        writer.writeCharacters('stuff ]]')
        writer.writeEndCDATA();

        writer.writeStartCDATA()
        writer.writeCharacters('> more stuff')
        writer.writeEndCDATA();

        writer.writeEndElement()

        then:
        xml.contains('<root><![CDATA[stuff ]]]]><![CDATA[> more stuff]]></root>')
    }

    def "is a Writer implementation that escapes characters"() {
        when:
        writer.writeStartElement("root")
        writer.write("some <chars>")
        writer.write(" and ".toCharArray())
        writer.write("x some x".toCharArray(), 2, 4)
        writer.write(' ')
        writer.writeStartCDATA()
        writer.write("cdata")
        writer.writeEndCDATA()
        writer.writeEndElement()

        then:
        xml.contains("<root>some &lt;chars&gt; and some <![CDATA[cdata]]></root>")
    }

    def "cannot end element when stack is empty"() {
        writer.writeStartElement("root")
        writer.writeEndElement()

        when:
        writer.writeEndElement()

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot end element, as there are no started elements.'
    }

    def "cannot end element when CDATA node is open"() {
        writer.writeStartElement("root")
        writer.writeStartCDATA()

        when:
        writer.writeEndElement()

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot end element, as current CDATA node has not been closed.'
    }

    def "cannot start element when CDATA node is open"() {
        writer.writeStartElement("root")
        writer.writeStartCDATA()

        when:
        writer.writeStartElement("nested")

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot start element, as current CDATA node has not been closed.'
    }

    def "cannot start CDATA node when CDATA node is open"() {
        writer.writeStartElement("root")
        writer.writeStartCDATA()

        when:
        writer.writeStartCDATA()

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot start CDATA node, as current CDATA node has not been closed.'
    }

    def "cannot end CDATA node when not in a CDATA node"() {
        writer.writeStartElement("root")

        when:
        writer.writeEndCDATA()

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot end CDATA node, as not currently in a CDATA node.'
    }

    def "closes tags"() {
        when:
        writer.writeStartElement("root")
        action.call(writer)
        writer.writeEndElement()

        then:
        sw.toString().contains("<root>") //is closed with '>'

        where:
        action << [{ it.writeStartElement("foo"); it.writeEndElement() },
                { it.writeStartCDATA(); it.writeEndCDATA() },
                { it.writeCharacters("bar") },
                { it.write("close") }]
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
        action << [{ it.writeStartElement("foo"); it.writeEndElement() },
                { it.writeStartCDATA(); it.writeEndCDATA() },
                { it.writeCharacters("bar") },
                { it.write("close") }]
    }

    def "outputs empty element when element has no content"() {
        when:
        writer.writeStartElement("root")
        writer.writeStartElement("empty").writeEndElement()
        writer.writeStartElement("empty").attribute("property", "value").writeEndElement()
        writer.writeEndElement()

        then:
        xml.contains('<root><empty/><empty property="value"/></root>')
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