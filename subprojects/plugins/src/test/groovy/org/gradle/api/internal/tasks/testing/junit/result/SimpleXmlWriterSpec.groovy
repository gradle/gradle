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

package org.gradle.api.internal.tasks.testing.junit.result

import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 12/3/12
 */
class SimpleXmlWriterSpec extends Specification {

    private sw = new StringWriter()
    private writer = new SimpleXmlWriter(sw)

    String getXml() {
        sw.toString()
    }

    def "writes basic xml"() {
        when:
        writer.writeXmlDeclaration("UTF-9", "1.23")
        writer.writeStartElement(writer.element("root").attribute("items", "9"))
        writer.writeEmptyElement("item")
        writer.writeStartElement(writer.element("item").attribute("size", "10m"))
        writer.writeCharacters("some chars")
        writer.writeStartElement("foo")
        writer.writeEndElement()
        writer.writeEndElement()
        writer.writeEndElement()

        then:
        xml == '<?xml version="1.23" encoding="UTF-9"?><root items="9"><item/><item size="10m">some chars<foo></foo></item></root>'
    }

    def "encodes for xml"() {
        when:
        writer.writeStartElement("root")
        writer.writeStartElement(writer.element("item").attribute("size", "encoded: &lt; < > ' \""))
        writer.writeCharacters("chars with interesting stuff: &lt; < > ' \"")
        writer.writeEndElement()
        writer.writeEndElement()

        then:
        xml == '<root><item size="encoded: &amp;lt; &lt; &gt; &apos; &quot;">chars with interesting stuff: &amp;lt; &lt; &gt; &apos; &quot;</item></root>'
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
        writer.writeCDATA('html allowed: <>'.toCharArray())
        writer.writeEndCDATA()

        writer.writeEndElement()

        then:
        xml == '<root><stuff><![CDATA[hey joe]]></stuff><![CDATA[encodes: ]]]]><![CDATA[> html allowed: <>]]></root>'
    }

    def "encodes CDATA when token on the border"() {
        when:
        //the end token is on the border of both char arrays
        writer.writeCDATA('stuff ]]'.toCharArray())
        writer.writeCDATA('> more stuff'.toCharArray())
        then:
        xml == 'stuff ]]]]><![CDATA[> more stuff'
    }

    def "does not encode CDATA when token separated in different CDATAs"() {
        when:
        //the end token is on the border of both char arrays

        writer.writeStartCDATA();
        writer.writeCDATA('stuff ]]'.toCharArray())
        writer.writeEndCDATA();

        writer.writeStartCDATA()
        writer.writeCDATA('> more stuff'.toCharArray())
        writer.writeEndCDATA();

        then:
        xml == '<![CDATA[stuff ]]]]><![CDATA[> more stuff]]>'
    }

    def "has basic stack validation"() {
        writer.writeStartElement("root")
        writer.writeEndElement()

        when:
        writer.writeEndElement()

        then:
        thrown(IllegalStateException)
    }

    def "allows xml declaration at the beginning only"() {
        writer.writeXmlDeclaration("utf-8", "1.0")

        when:
        writer.writeXmlDeclaration("utf-8", "1.0")

        then:
        thrown(IllegalStateException)
    }
}
