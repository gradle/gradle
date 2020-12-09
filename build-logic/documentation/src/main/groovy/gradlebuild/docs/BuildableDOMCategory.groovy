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

import groovy.xml.dom.DOMCategory
import org.w3c.dom.Element
import org.w3c.dom.Node

class BuildableDOMCategory extends DOMCategory {
    public static void setText(Element element, String value) {
        while (element.hasChildNodes()) {
            element.removeChild(element.getFirstChild())
        }
        element.appendChild(element.ownerDocument.createTextNode(value))
    }

    public static void setChildren(Node element, Closure cl) {
        while (element.hasChildNodes()) {
            element.removeChild(element.getFirstChild())
        }
        leftShift(element, cl)
    }

    public static def leftShift(Node parent, Closure cl) {
        DomBuilder builder = new DomBuilder(parent)
        cl.delegate = builder
        cl.call()
        return builder.elements[0]
    }

    public static void leftShift(Node parent, Node node) {
        parent.appendChild(parent.ownerDocument.importNode(node, true))
    }

    public static void addFirst(Node parent, Closure cl) {
        DomBuilder builder = new DomBuilder(parent.ownerDocument, null)
        cl.delegate = builder
        cl.call()
        def firstChild = parent.firstChild
        builder.elements.each { element ->
            parent.insertBefore(element, firstChild)
        }
    }

    public static void addBefore(Node sibling, Closure cl) {
        DomBuilder builder = new DomBuilder(sibling.ownerDocument, null)
        cl.delegate = builder
        cl.call()
        def parent = sibling.parentNode
        builder.elements.each { element ->
            parent.insertBefore(element, sibling)
        }
    }

    public static void addBefore(Element sibling, Node n) {
        def parent = sibling.parentNode
        parent.insertBefore(n, sibling)
    }

    public static Object addAfter(Element sibling, Closure cl) {
        DomBuilder builder = new DomBuilder(sibling.ownerDocument, null)
        cl.delegate = builder
        cl.call()
        def parent = sibling.parentNode
        def next = sibling.nextSibling
        builder.elements.each { element ->
            parent.insertBefore(element, next)
        }
        return builder.elements.size() == 1 ? builder.elements[0] : builder.elements
    }
}
