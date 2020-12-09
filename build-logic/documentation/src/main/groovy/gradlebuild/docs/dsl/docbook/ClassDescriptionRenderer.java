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

package gradlebuild.docs.dsl.docbook;

import gradlebuild.docs.dsl.docbook.model.ClassDoc;
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

        addApiDocumentationLink(classDoc, parent, document);
        addSubtypeLinks(classDoc, parent, document);

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

    private void addApiDocumentationLink(ClassDoc classDoc, Element parent, Document document) {
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
    }

    private void addSubtypeLinks(ClassDoc classDoc, Element parent, Document document) {
        if (!classDoc.getSubClasses().isEmpty()) {
            Element list = document.createElement("segmentedlist");
            parent.appendChild(list);
            Element segtitle = document.createElement("segtitle");
            list.appendChild(segtitle);
            segtitle.appendChild(document.createTextNode("Known Subtypes"));
            Element listItem = document.createElement("seglistitem");
            list.appendChild(listItem);
            Element seg = document.createElement("seg");
            listItem.appendChild(seg);
            Element simplelist = document.createElement("simplelist");

            int columns = 3;
            if (classDoc.getSubClasses().size() <= 3) {
                // if there are only 3 or fewer known subtypes, render them
                // in a single column
                columns = 1;
            }
            simplelist.setAttribute("columns", String.valueOf(columns));
            simplelist.setAttribute("type", "vert");
            for (ClassDoc subClass : classDoc.getSubClasses()) {
                Element member = document.createElement("member");
                Element apilink = document.createElement("apilink");
                apilink.setAttribute("class", subClass.getName());
                member.appendChild(apilink);
                simplelist.appendChild(member);
            }
            seg.appendChild(simplelist);
        }
    }
}
