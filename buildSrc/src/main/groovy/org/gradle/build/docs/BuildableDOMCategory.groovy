package org.gradle.build.docs

import org.w3c.dom.Element

class BuildableDOMCategory {
    public static setText(Element element, String value) {
        while (element.hasChildNodes()) {
            element.removeChild(element.getFirstChild())
        }
        element.appendChild(element.ownerDocument.createTextNode(value))
    }

    public static leftShift(Element parent, Closure cl) {
        DomBuilder builder = new DomBuilder(parent)
        cl.delegate = builder
        cl.call()
    }
}
