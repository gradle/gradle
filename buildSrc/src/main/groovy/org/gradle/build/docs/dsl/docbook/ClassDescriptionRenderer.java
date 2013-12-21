/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.build.docs.dsl.docbook;

import org.gradle.build.docs.dsl.docbook.model.ClassDoc;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ClassDescriptionRenderer {
    private final ElementWarningsRenderer warningsRenderer = new ElementWarningsRenderer();

    public void renderTo(ClassDoc classDoc, Element parent) {
        Document document = parent.getOwnerDocument();

        Element title = document.createElement("title");
        parent.appendChild(title);
        title.appendChild(document.createTextNode(classDoc.getSimpleName()));

        Element list = document.createElement("segmentedlist");
        parent.appendChild(list);
        Element segtitle = document.createElement("segtitle");
        list.appendChild(segtitle);
        segtitle.appendChild(document.createTextNode("API Documentation"));
        Element listItem = document.createElement("seglistitem");
        list.appendChild(listItem);
        Element seg = document.createElement("seg");
        listItem.appendChild(seg);
        Element apilink = document.createElement("apilink");
        seg.appendChild(apilink);
        apilink.setAttribute("class", classDoc.getName());
        apilink.setAttribute("style", classDoc.getStyle());

        warningsRenderer.renderTo(classDoc, "class", parent);

        for (Element element : classDoc.getComment()) {
            parent.appendChild(document.importNode(element, true));
        }
        NodeList otherContent = classDoc.getClassSection().getChildNodes();
        for (int i = 0; i < otherContent.getLength(); i++) {
            Node child = otherContent.item(i);
            if (child instanceof Element && !((Element) child).getTagName().equals("section")) {
                parent.appendChild(document.importNode(child, true));
            }
        }
    }
}
