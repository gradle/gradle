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
package org.gradle.api.internal

import spock.lang.Specification
import org.gradle.api.artifacts.maven.XmlProvider
import org.gradle.api.Action
import org.gradle.util.TextUtil

class XmlTransformerTest extends Specification {
    final XmlTransformer transformer = new XmlTransformer()

    def returnsOriginalStringWhenNoActions() {
        expect:
        transformer.transform('<xml/>') == '<xml/>'
    }

    def actionCanAccessXmlAsStringBuffer() {
        Action<XmlProvider> action = Mock()
        transformer.addAction(action)

        when:
        def result = transformer.transform('<xml/>')

        then:
        result == '<some-xml/>'
        1 * action.execute(!null) >> { args ->
            def provider = args[0]
            provider.asString().insert(1, 'some-')
        }
    }

    def actionCanAccessXmlAsNode() {
        Action<XmlProvider> action = Mock()
        transformer.addAction(action)

        when:
        def result = transformer.transform('<xml/>')

        then:
        result == '<xml>\n  <child1/>\n</xml>\n'
        1 * action.execute(!null) >> { args ->
            def provider = args[0]
            provider.asNode().appendNode('child1')
        }
    }

    def actionCanAccessXmlAsDomElement() {
        Action<XmlProvider> action = Mock()
        transformer.addAction(action)

        when:
        def result = transformer.transform('<xml/>')

        then:
        result == toNative('<xml>\n  <child1/>\n</xml>\n')
        1 * action.execute(!null) >> { args ->
            def provider = args[0]
            def document = provider.asElement().ownerDocument
            provider.asElement().appendChild(document.createElement('child1'))
        }
    }

    def canTransformStringToAWriter() {
        Action<XmlProvider> action = Mock()
        transformer.addAction(action)
        StringWriter writer = new StringWriter()

        when:
        transformer.transform('<xml/>', writer)

        then:
        writer.toString() == '<xml>\n  <child1/>\n</xml>\n'
        1 * action.execute(!null) >> { args ->
            def provider = args[0]
            provider.asNode().appendNode('child1')
        }
    }

    def canTransformNodeToAWriter() {
        Action<XmlProvider> action = Mock()
        transformer.addAction(action)
        StringWriter writer = new StringWriter()
        Node node = new XmlParser().parseText('<xml/>')

        when:
        transformer.transform(node, writer)

        then:
        writer.toString() == '<xml>\n  <child1/>\n</xml>\n'
        1 * action.execute(!null) >> { args ->
            def provider = args[0]
            provider.asNode().appendNode('child1')
        }
    }

    def canUseAClosureAsAnAction() {
        transformer.addAction { provider ->
            provider.asNode().appendNode('child1')
        }
        StringWriter writer = new StringWriter()

        when:
        transformer.transform('<xml/>', writer)

        then:
        writer.toString() == '<xml>\n  <child1/>\n</xml>\n'
    }

    def canChainActions() {
        Action<XmlProvider> stringAction = Mock()
        Action<XmlProvider> nodeAction = Mock()
        Action<XmlProvider> elementAction = Mock()
        Action<XmlProvider> stringAction2 = Mock()
        transformer.addAction(stringAction)
        transformer.addAction(elementAction)
        transformer.addAction(nodeAction)
        transformer.addAction(stringAction2)

        when:
        def result = transformer.transform('<xml/>')

        then:
        result == '<some-xml>\n  <child1/>\n  <child2/>\n</some-xml>\n<!-- end -->'
        1 * stringAction.execute(!null) >> { args ->
            def provider = args[0]
            provider.asString().insert(1, 'some-')
        }
        1 * elementAction.execute(!null) >> { args ->
            def provider = args[0]
            def document = provider.asElement().ownerDocument
            provider.asElement().appendChild(document.createElement('child1'))
        }
        1 * nodeAction.execute(!null) >> { args ->
            def provider = args[0]
            provider.asNode().appendNode('child2')
        }
        1 * stringAction2.execute(!null) >> { args ->
            def provider = args[0]
            provider.asString().append('<!-- end -->')
        }
    }

    def canChainNodeActions() {
        Action<XmlProvider> nodeAction = Mock()
        Action<XmlProvider> nodeAction2 = Mock()
        transformer.addAction(nodeAction)
        transformer.addAction(nodeAction2)

        when:
        def result = transformer.transform('<xml/>')

        then:
        result == '<xml>\n  <child1/>\n  <child2/>\n</xml>\n'
        1 * nodeAction.execute(!null) >> { args ->
            def provider = args[0]
            provider.asNode().appendNode('child1')
        }
        1 * nodeAction2.execute(!null) >> { args ->
            def provider = args[0]
            provider.asNode().appendNode('child2')
        }
    }

    def "correct indentation used when writing out groovy.util.Node"() {
        transformer.indentation = "\t"
        transformer.addAction { XmlProvider provider -> provider.asNode().children()[0].appendNode("grandchild") }

        expect:
        transformer.transform("<xml>\n    <child/>\n</xml>\n") == "<xml>\n\t<child>\n\t\t<grandchild/>\n\t</child>\n</xml>\n"
    }

    def "incorrect indentation used when writing out org.w3c.dom.Element"() {
        transformer.indentation = "\t"
        transformer.addAction { XmlProvider provider ->
            def document = provider.asElement().ownerDocument
            document.getElementsByTagName("child").item(0).appendChild(document.createElement("grandchild"))
        }

        expect:
        transformer.transform("<xml>\n    <child/>\n</xml>\n") == toNative("<xml>\n    <child>\n    <grandchild/>\n  </child>\n</xml>\n")
    }

    def String toNative(String value) {
        return value.replaceAll('\n', TextUtil.LINE_SEPARATOR)
    }
}
