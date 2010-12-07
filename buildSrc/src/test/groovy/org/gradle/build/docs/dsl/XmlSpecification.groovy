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
package org.gradle.build.docs.dsl

import spock.lang.Specification
import org.w3c.dom.Document
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Node
import org.w3c.dom.Element
import org.w3c.dom.Text
import org.w3c.dom.Attr
import javax.xml.parsers.DocumentBuilder
import org.xml.sax.InputSource

class XmlSpecification extends Specification {
    final Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()

    def parse(String str) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance()
        factory.setNamespaceAware(true)
        DocumentBuilder builder = factory.newDocumentBuilder()
        return builder.parse(new InputSource(new StringReader(str))).documentElement
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
            format(node, builder, 0, prettyPrint)
        }
        return builder.toString()
    }

    def format(Node node, Appendable target, int depth, boolean prettyPrint) {
        if (node instanceof Element) {
            Element element = (Element) node

            if (prettyPrint && depth > 0) {
                target.append('\n')
                depth.times { target.append('    ') }
            }

            target.append("<${element.tagName}")
            for (int i = 0; i < element.attributes.length; i++) {
                Attr attr = element.attributes.item(i)
                target.append(" $attr.name=\"$attr.value\"")
            }

            List<Node> trimmedContent = prettyPrint ? element.childNodes.inject([]) { list, child ->
                if (child instanceof Text && child.textContent.trim().length() == 0) {
                    return list
                }
                list << child
                return list
            } : element.childNodes.collect { it }
            if (trimmedContent.isEmpty()) {
                target.append('/>')
                return
            }
            target.append('>')

            boolean hasChildElements = trimmedContent.find { it instanceof Element }

            trimmedContent.each { child ->
                format(child, target, depth + 1, prettyPrint)
            }

            if (prettyPrint && hasChildElements) {
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
