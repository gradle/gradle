package org.gradle.build.docs

import org.w3c.dom.Element

class BuildableDOMCategory {
    public static setText(Element element, String value) {
        while (element.hasChildNodes()) {
            element.removeChild(element.getFirstChild())
        }
        element.appendChild(element.ownerDocument.createTextNode(value))
    }

    public static setChildren(Element element, Closure cl) {
        while (element.hasChildNodes()) {
            element.removeChild(element.getFirstChild())
        }
        leftShift(element, cl)
    }

    public static leftShift(Element parent, Closure cl) {
        DomBuilder builder = new DomBuilder(parent)
        cl.delegate = builder
        cl.call()
    }

    public static leftShift(Element parent, Element node) {
        parent.appendChild(parent.ownerDocument.importNode(node, true))
    }

    public static addFirst(Element parent, Closure cl) {
        DomBuilder builder = new DomBuilder(parent.ownerDocument, null)
        cl.delegate = builder
        cl.call()
        def firstChild = parent.firstChild
        builder.elements.each { element ->
            parent.insertBefore(element, firstChild)
        }
    }
}
