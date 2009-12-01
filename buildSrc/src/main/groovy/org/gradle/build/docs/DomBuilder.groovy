package org.gradle.build.docs

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class DomBuilder extends BuilderSupport {
    Document document

    def DomBuilder(document) {
        this.document = document;
    }

    protected Element createNode(Object name) {
        Element element = document.createElement(name as String)
        if (document.documentElement == null) {
            document.appendChild(element)
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
        element.appendChild(document.createTextNode(value as String))
        return element
    }

    protected Element createNode(Object name, Object value) {
        return createNode(name, [:], value)
    }

    protected void setParent(Object parent, Object child) {
        parent.appendChild(child)
    }

    def appendChild(Node node) {
        current.appendChild(document.importNode(node, true))
    }

    def text(String text) {
        current.appendChild(document.createTextNode(text))
    }
}



