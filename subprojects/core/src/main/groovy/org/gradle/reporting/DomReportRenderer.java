/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.reporting;

import org.w3c.dom.Element;

public abstract class DomReportRenderer<T> {
    /**
     * Renders the report for the given model as children of the given DOM element.
     */
    public abstract void render(T model, Element parent);

    protected Element append(Element parent, String name) {
        Element element = parent.getOwnerDocument().createElement(name);
        parent.appendChild(element);
        return element;
    }

    protected Element appendWithId(Element parent, String name, String id) {
        Element element = parent.getOwnerDocument().createElement(name);
        parent.appendChild(element);
        element.setAttribute("id", id);
        return element;
    }

    protected Element appendWithText(Element parent, String name, Object textContent) {
        Element element = parent.getOwnerDocument().createElement(name);
        parent.appendChild(element);
        appendText(element, textContent);
        return element;
    }

    protected void appendText(Element element, Object textContent) {
        element.appendChild(element.getOwnerDocument().createTextNode(textContent.toString()));
    }

    protected Element appendLink(Element parent, String href, Object textContent) {
        Element element = appendWithText(parent, "a", textContent);
        element.setAttribute("href", href);
        return element;
    }
}
