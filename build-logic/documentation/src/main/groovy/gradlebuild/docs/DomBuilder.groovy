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

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList

class DomBuilder extends BuilderSupport {
    Document document
    Node parent
    List elements = []

    def DomBuilder(Document document) {
        this.document = document
        this.parent = document
    }

    def DomBuilder(Node parent) {
        this.document = parent.ownerDocument
        this.parent = parent
    }

    def DomBuilder(Document document, Node parent) {
        this.document = document
        this.parent = parent
    }

    protected Element createNode(Object name) {
        Element element = document.createElement(name as String)
        if (getCurrent() == null) {
            elements << element
            parent?.appendChild(element)
        }
        return element
    }

    protected Element createNode(Object name, Map attributes) {
        Element element = createNode(name)
        attributes.each {key, value ->
            element.setAttribute(key as String, value as String)
        }
        return element
    }

    protected Element createNode(Object name, Map attributes, Object value) {
        Element element = createNode(name, attributes)
        if (value instanceof Node) {
            element.appendChild(document.importNode(value, true))
        } else {
            element.appendChild(document.createTextNode(value as String))
        }
        return element
    }

    protected Element createNode(Object name, Object value) {
        return createNode(name, [:], value)
    }

    protected void setParent(Object parent, Object child) {
        parent.appendChild(child)
    }

    def appendChild(Node node) {
        if (!current) {
            elements << (Element) document.importNode(node, true)
        } else  {
            current.appendChild(document.importNode(node, true))
        }
    }

    def appendChildren(Iterable<? extends Node> nodes) {
        nodes.each { appendChild(it) }
    }

    def appendChildren(NodeList nodes) {
        nodes.each { appendChild(it) }
    }

    def text(String text) {
        current.appendChild(document.createTextNode(text))
    }
}



