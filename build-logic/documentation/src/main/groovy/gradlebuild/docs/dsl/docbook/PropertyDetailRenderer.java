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

import gradlebuild.docs.dsl.docbook.model.ExtraAttributeDoc;
import gradlebuild.docs.dsl.docbook.model.PropertyDoc;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class PropertyDetailRenderer {
    private final GenerationListener listener;
    private final LinkRenderer linkRenderer;
    private final ElementWarningsRenderer warningsRenderer = new ElementWarningsRenderer();

    public PropertyDetailRenderer(LinkRenderer linkRenderer, GenerationListener listener) {
        this.linkRenderer = linkRenderer;
        this.listener = listener;
    }

    public void renderTo(PropertyDoc propertyDoc, Element parent) {
        Document document = parent.getOwnerDocument();
        Element section = document.createElement("section");
        parent.appendChild(section);
        section.setAttribute("id", propertyDoc.getId());
        section.setAttribute("role", "detail");

        Element title = document.createElement("title");
        section.appendChild(title);
        title.appendChild(linkRenderer.link(propertyDoc.getMetaData().getType(), listener));
        title.appendChild(document.createTextNode(" "));
        Element literal = document.createElement("literal");
        title.appendChild(literal);
        literal.appendChild(document.createTextNode(propertyDoc.getName()));

        if (!propertyDoc.getMetaData().isProviderApi()) {
            if (!propertyDoc.getMetaData().isWriteable()) {
                title.appendChild(document.createTextNode(" (read-only)"));
            } else if (!propertyDoc.getMetaData().isReadable()) {
                title.appendChild(document.createTextNode(" (write-only)"));
            }
        }

        warningsRenderer.renderTo(propertyDoc, "property", section);

        for (Element element : propertyDoc.getComment()) {
            section.appendChild(document.importNode(element, true));
        }

        if (!propertyDoc.getAdditionalValues().isEmpty()) {
            Element segmentedlist = document.createElement("segmentedlist");
            section.appendChild(segmentedlist);
            for (ExtraAttributeDoc attributeDoc : propertyDoc.getAdditionalValues()) {
                Element segtitle = document.createElement("segtitle");
                segmentedlist.appendChild(segtitle);
                for (Node node : attributeDoc.getTitle()) {
                    segtitle.appendChild(document.importNode(node, true));
                }
            }
            Element seglistitem = document.createElement("seglistitem");
            segmentedlist.appendChild(seglistitem);
            for (ExtraAttributeDoc attributeDoc : propertyDoc.getAdditionalValues()) {
                Element seg = document.createElement("seg");
                seglistitem.appendChild(seg);
                for (Node node : attributeDoc.getValue()) {
                    seg.appendChild(document.importNode(node, true));
                }
            }
        }
    }

}
