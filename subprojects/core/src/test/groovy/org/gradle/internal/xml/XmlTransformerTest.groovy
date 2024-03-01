/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.internal.xml

import groovy.xml.XmlParser
import org.gradle.api.Action
import org.gradle.api.XmlProvider
import org.gradle.api.internal.DomNode
import org.gradle.internal.UncheckedException
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.Specification

import javax.xml.parsers.DocumentBuilderFactory

class XmlTransformerTest extends Specification {
    final XmlTransformer transformer = new XmlTransformer()
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "returns original string when no actions are provided"() {
        expect:
        looksLike '<root/>', transformer.transform('<root/>')
    }

    def "action can access XML as StringBuilder"() {
        Action<XmlProvider> action = Mock()
        transformer.addAction(action)

        when:
        def result = transformer.transform('<root/>')

        then:
        action.execute(_) >> { XmlProvider provider ->
            def builder = provider.asString()
            builder.insert(builder.indexOf("root"), 'some-')
        }
        looksLike '<some-root/>', result
    }

    def "action can access XML as Node"() {
        Action<XmlProvider> action = Mock()
        transformer.addAction(action)

        when:
        def result = transformer.transform('<root/>')

        then:
        action.execute(_) >> { XmlProvider provider ->
            provider.asNode().appendNode('child1')
        }
        looksLike '<root>\n  <child1/>\n</root>\n', result
    }

    def "action can access XML as DOM element"() {
        Action<XmlProvider> action = Mock()
        transformer.addAction(action)

        when:
        def result = transformer.transform('<root/>')

        then:
        action.execute(_) >> { XmlProvider provider ->
            def document = provider.asElement().ownerDocument
            provider.asElement().appendChild(document.createElement('child1'))
        }
        looksLike '<root>\n  <child1/>\n</root>\n', result
    }

    def "can transform String to a Writer"() {
        Action<XmlProvider> action = Mock()
        transformer.addAction(action)
        StringWriter writer = new StringWriter()

        when:
        transformer.transform('<root/>', writer)

        then:
        action.execute(_) >> { XmlProvider provider ->
            provider.asNode().appendNode('child1')
        }
        looksLike '<root>\n  <child1/>\n</root>\n', writer.toString()
    }

    def "can transform String to an OutputStream"() {
        Action<XmlProvider> action = Mock()
        transformer.addAction(action)
        def outputStream = new ByteArrayOutputStream()

        when:
        transformer.transform('<root/>', outputStream)

        then:
        action.execute(_) >> { XmlProvider provider ->
            provider.asNode().appendNode('child\u03b1')
        }
        looksLike '<root>\n  <child\u03b1/>\n</root>\n', outputStream.toByteArray()
    }

    def "can transform Node to a Writer"() {
        Action<XmlProvider> action = Mock()
        transformer.addAction(action)
        StringWriter writer = new StringWriter()
        Node node = new XmlParser().parseText('<root/>')

        when:
        transformer.transform(node, writer)

        then:
        action.execute(_) >> { XmlProvider provider ->
            provider.asNode().appendNode('child1')
        }
        looksLike '<root>\n  <child1/>\n</root>\n', writer.toString()
    }

    def "can transform Node to an OutputStream"() {
        Action<XmlProvider> action = Mock()
        transformer.addAction(action)
        def outputStream = new ByteArrayOutputStream()
        Node node = new XmlParser().parseText('<root/>')

        when:
        transformer.transform(node, outputStream)

        then:
        action.execute(_) >> { XmlProvider provider ->
            provider.asNode().appendNode('child\u03b1')
        }
        looksLike '<root>\n  <child\u03b1/>\n</root>\n', outputStream.toByteArray()
    }

    def "can transform Node to a File"() {
        Action<XmlProvider> action = Mock()
        transformer.addAction(action)
        File file = tmpDir.file("out.xml")
        Node node = new XmlParser().parseText('<root/>')

        when:
        transformer.transform(node, file)

        then:
        action.execute(_) >> { XmlProvider provider ->
            provider.asNode().appendNode('child\u03b1')
        }
        looksLike '<root>\n  <child\u03b1/>\n</root>\n', file.bytes
    }

    def "can use a closure as an action"() {
        transformer.addAction { provider ->
            provider.asNode().appendNode('child1')
        }
        StringWriter writer = new StringWriter()

        when:
        transformer.transform('<root/>', writer)

        then:
        looksLike '<root>\n  <child1/>\n</root>\n', writer.toString()
    }

    def "can chain actions"() {
        Action<XmlProvider> stringAction = Mock()
        Action<XmlProvider> nodeAction = Mock()
        Action<XmlProvider> elementAction = Mock()
        Action<XmlProvider> stringAction2 = Mock()
        transformer.addAction(stringAction)
        transformer.addAction(elementAction)
        transformer.addAction(nodeAction)
        transformer.addAction(stringAction2)

        when:
        def result = transformer.transform('<root/>')

        then:
        stringAction.execute(_) >> { XmlProvider provider ->
            def builder = provider.asString()
            builder.insert(builder.indexOf("root"), 'some-')
        }
        nodeAction.execute(_) >> { XmlProvider provider ->
            provider.asNode().appendNode('child2')
        }
        elementAction.execute(_) >> { XmlProvider provider ->
            def document = provider.asElement().ownerDocument
            provider.asElement().appendChild(document.createElement('child1'))
        }
        stringAction2.execute(_) >> { XmlProvider provider ->
            provider.asString().append('<!-- end -->')
        }

        looksLike '<some-root>\n  <child1/>\n  <child2/>\n</some-root>\n<!-- end -->', result
    }

    def "can chain node actions"() {
        Action<XmlProvider> nodeAction = Mock()
        Action<XmlProvider> nodeAction2 = Mock()
        transformer.addAction(nodeAction)
        transformer.addAction(nodeAction2)

        when:
        def result = transformer.transform('<root/>')

        then:
        nodeAction.execute(_) >> { XmlProvider provider ->
            provider.asNode().appendNode('child1')
        }
        nodeAction2.execute(_) >> { XmlProvider provider ->
            provider.asNode().appendNode('child2')
        }
        looksLike '<root>\n  <child1/>\n  <child2/>\n</root>\n', result
    }

    def "indentation correct when writing out Node"() {
        transformer.indentation = "\t"
        transformer.addAction { XmlProvider provider -> provider.asNode().children()[0].appendNode("grandchild") }

        when:
        def result = transformer.transform("<root>\n  <child/>\n</root>\n")

        then:
        looksLike "<root>\n\t<child>\n\t\t<grandchild/>\n\t</child>\n</root>\n", result
    }

    def "can add DOCTYPE along with nodes"() {
        transformer.addAction { it.asNode().appendNode('someChild') }
        transformer.addAction {
            def s = it.asString()
            s.insert(s.indexOf("?>") + 2, '\n<!DOCTYPE application PUBLIC "-//Sun Microsystems, Inc.//DTD J2EE Application 1.3//EN" "http://java.sun.com/dtd/application_1_3.dtd">')
        }

        when:
        def result = transformer.transform("<root></root>")

        then:
        looksLike "<!DOCTYPE application PUBLIC \"-//Sun Microsystems, Inc.//DTD J2EE Application 1.3//EN\" \"http://java.sun.com/dtd/application_1_3.dtd\">\n<root>\n  <someChild/>\n</root>\n", result
    }

    def "can specify DOCTYPE when using DomNode"() {
        StringWriter writer = new StringWriter()
        def node = new DomNode('root')
        node.publicId = 'public-id'
        node.systemId = 'system-id'

        when:
        transformer.transform(node, writer)

        then:
        looksLike '''<!DOCTYPE root PUBLIC "public-id" "system-id">
<root/>
''', writer.toString()
    }

    def "DOCTYPE is preserved when transformed as a Node"() {
        StringWriter writer = new StringWriter()
        def node = new DomNode('root')
        node.publicId = 'public-id'
        node.systemId = 'system-id'
        transformer.addAction { it.asNode().appendNode('someChild') }

        when:
        transformer.transform(node, writer)

        then:
        looksLike '''<!DOCTYPE root PUBLIC "public-id" "system-id">
<root>
  <someChild/>
</root>
''', writer.toString()
    }

    def "DOCTYPE with DTD is not allowed when transformed as a DOM element"() {
        StringWriter writer = new StringWriter()
        def node = new DomNode('root')
        node.publicId = 'public-id'
        node.systemId = tmpDir.createFile("thing.dtd").toURI()
        transformer.addAction { it.asElement().appendChild(it.asElement().ownerDocument.createElement('someChild')) }

        when:
        transformer.transform(node, writer)

        then:
        def e = thrown(UncheckedException)
        e.message.contains("External DTD: Failed to read external DTD 'thing.dtd', because 'file' access is not allowed")
    }

    def "indentation correct when writing out DOM element (only) if indenting with spaces"() {
        transformer.indentation = expected
        transformer.addAction { XmlProvider provider ->
            def document = provider.asElement().ownerDocument
            document.getElementsByTagName("child").item(0).appendChild(document.createElement("grandchild"))
        }

        when:
        def result = transformer.transform("<root>\n  <child/>\n</root>\n")

        then:
        looksLike("<root>\n$actual<child>\n$actual$actual<grandchild/>\n$actual</child>\n</root>\n", result)

        where:
        expected | actual
        "    "   | "    "
        "\t"     | "  " // tabs not supported, two spaces used instead
    }

    def "empty text nodes are removed when writing out DOM element"() {
        transformer.addAction { XmlProvider provider ->
            def document = provider.asElement().ownerDocument
            document.getElementsByTagName("child").item(0).appendChild(document.createElement("grandchild"))
            document.getElementsByTagName("child").item(0).appendChild(document.createTextNode("         "))
            document.getElementsByTagName("child").item(0).appendChild(document.createElement("grandchild"))
        }

        when:
        def result = transformer.transform("<root>\n<child/>\n</root>\n")

        then:
        looksLike("<root>\n  <child>\n    <grandchild/>\n    <grandchild/>\n  </child>\n</root>\n", result)
    }

    def "can use with action api"() {
        given:
        def writer = new StringWriter()
        def input = "<things><thing/></things>"
        def generator = new Action<Writer>() {
            void execute(Writer t) {
                t.write(input)
            }
        }

        when:
        transformer.transform(writer, generator)

        then:
        looksLike(input, writer.toString())

        when:
        writer.buffer.setLength(0)
        transformer.addAction(new Action<XmlProvider>() {
            void execute(XmlProvider xml) {
                xml.asNode().thing[0].@foo = "bar"
            }
        })
        transformer.transform(writer, generator)

        then:
        looksLike('<things>\n  <thing foo="bar"/>\n</things>', writer.toString())
    }

    private void looksLike(String expected, String actual) {
        assert removeTrailingWhitespace(actual) == removeTrailingWhitespace(TextUtil.toPlatformLineSeparators("<?xml version=\"1.0\"?>\n" + expected))
    }

    private void looksLike(String expected, byte[] actual) {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(actual))
        assert removeTrailingWhitespace(new String(actual, "utf-8")) == removeTrailingWhitespace(TextUtil.toPlatformLineSeparators("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + expected))
    }

    private String removeTrailingWhitespace(String value) {
        return value.replaceFirst('(?s)\\s+$', "")
    }
}
