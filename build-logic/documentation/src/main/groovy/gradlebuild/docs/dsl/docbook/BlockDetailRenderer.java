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

import gradlebuild.docs.dsl.docbook.model.BlockDoc;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class BlockDetailRenderer {
    private final GenerationListener listener;
    private final LinkRenderer linkRenderer;
    private final ElementWarningsRenderer warningsRenderer = new ElementWarningsRenderer();

    public BlockDetailRenderer(LinkRenderer linkRenderer, GenerationListener listener) {
        this.linkRenderer = linkRenderer;
        this.listener = listener;
    }

    public void renderTo(BlockDoc blockDoc, Element parent) {
        Document document = parent.getOwnerDocument();

        Element section = document.createElement("section");
        parent.appendChild(section);
        section.setAttribute("id", blockDoc.getId());
        section.setAttribute("role", "detail");

        Element title = document.createElement("title");
        section.appendChild(title);
        Element literal = document.createElement("literal");
        title.appendChild(literal);
        literal.appendChild(document.createTextNode(blockDoc.getName()));
        title.appendChild(document.createTextNode(" { }"));

        warningsRenderer.renderTo(blockDoc, "script block", section);

        for (Element element : blockDoc.getComment()) {
            section.appendChild(document.importNode(element, true));
        }

        Element segmentedlist = document.createElement("segmentedlist");
        section.appendChild(segmentedlist);
        Element segtitle = document.createElement("segtitle");
        segmentedlist.appendChild(segtitle);
        segtitle.appendChild(document.createTextNode("Delegates to"));
        Element seglistitem = document.createElement("seglistitem");
        segmentedlist.appendChild(seglistitem);
        Element seg = document.createElement("seg");
        seglistitem.appendChild(seg);
        if (blockDoc.isMultiValued()) {
            seg.appendChild(document.createTextNode("Each "));
            seg.appendChild(linkRenderer.link(blockDoc.getType(), listener));
            seg.appendChild(document.createTextNode(" in "));
            // TODO - add linkRenderer.link(property)
            Element link = document.createElement("link");
            seg.appendChild(link);
            link.setAttribute("linkend", blockDoc.getBlockProperty().getId());
            literal = document.createElement("literal");
            link.appendChild(literal);
            literal.appendChild(document.createTextNode(blockDoc.getBlockProperty().getName()));
        } else {
            seg.appendChild(linkRenderer.link(blockDoc.getType(), listener));
            seg.appendChild(document.createTextNode(" from "));
            // TODO - add linkRenderer.link(property)
            Element link = document.createElement("link");
            seg.appendChild(link);
            link.setAttribute("linkend", blockDoc.getBlockProperty().getId());
            literal = document.createElement("literal");
            link.appendChild(literal);
            literal.appendChild(document.createTextNode(blockDoc.getBlockProperty().getName()));

        }
    }
}
