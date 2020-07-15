/*
 * Copyright 2020 the original author or authors.
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
package gradlebuild.docs

import org.w3c.dom.*
import org.xml.sax.InputSource
import spock.lang.Specification

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

abstract class XmlSpecification extends Specification {
    final Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()

    def parse(String str, Document document = null) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance()
        factory.setNamespaceAware(true)
        DocumentBuilder builder = factory.newDocumentBuilder()
        def parsed = builder.parse(new InputSource(new StringReader(str))).documentElement
        return document ? document.importNode(parsed, true) : parsed
    }

    def formatTree(Closure cl) {
        withCategories {
            return formatTree(cl.call())
        }
    }

    def withCategories(Closure cl) {
        use(BuildableDOMCategory) {
            return cl.call()
        }
    }

    def format(Node... nodes) {
        format(nodes as List)
    }

    def formatTree(Node... nodes) {
        formatTree(nodes as List)
    }

    def formatTree(Iterable<? extends Node> nodes) {
        format(nodes, true)
    }

    def format(Iterable<? extends Node> nodes, boolean prettyPrint = false) {
        StringBuilder builder = new StringBuilder()
        nodes.each { node ->
            format(node, builder, 0, prettyPrint, prettyPrint)
        }
        return builder.toString()
    }

    def format(Node node, Appendable target, int depth, boolean prettyPrint, boolean indentSelf) {
        if (node instanceof Element) {
            Element element = (Element) node

            if (indentSelf && depth > 0) {
                target.append('\n')
                depth.times { target.append('    ') }
            }

            target.append("<${element.tagName}")
            for (int i = 0; i < element.attributes.length; i++) {
                Attr attr = element.attributes.item(i)
                target.append(" $attr.name=\"$attr.value\"")
            }

            element.childNodes.findAll { it instanceof Text }.each {
                assert it.textContent != null : "Found null text element in <$element.tagName>"
            }

            List<Node> trimmedContent = element.childNodes.collect { it };
            boolean inlineContent = trimmedContent.find { it instanceof Text && it.textContent.trim() }

            if (prettyPrint && !inlineContent) {
                trimmedContent = element.childNodes.inject([]) { list, child ->
                    if (!(child instanceof Text) || child.textContent.trim().length() != 0) {
                        list << child
                    }
                    return list
                }
            }

            if (trimmedContent.isEmpty()) {
                target.append('/>')
                return
            }
            target.append('>')


            trimmedContent.each { child ->
                format(child, target, depth + 1, prettyPrint, prettyPrint && !inlineContent)
            }

            if (prettyPrint && indentSelf && !inlineContent) {
                target.append('\n')
                depth.times { target.append('    ') }
            }

            target.append("</${element.tagName}>")

            return
        }

        if (node instanceof Text) {
            target.append(node.nodeValue.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;'))
            return
        }

        throw new UnsupportedOperationException("Don't know how to format DOM node: $node")
    }
}
