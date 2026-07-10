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

import gradlebuild.docs.dsl.docbook.model.MethodDoc;
import gradlebuild.docs.dsl.source.model.ParameterMetaData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;

public class MethodDetailRenderer {
    private final GenerationListener listener;
    private final LinkRenderer linkRenderer;
    private final ElementWarningsRenderer warningsRenderer = new ElementWarningsRenderer();

    public MethodDetailRenderer(LinkRenderer linkRenderer, GenerationListener listener) {
        this.linkRenderer = linkRenderer;
        this.listener = listener;
    }

    public void renderTo(MethodDoc methodDoc, Element parent) {
        Document document = parent.getOwnerDocument();

        Element section = document.createElement("section");
        parent.appendChild(section);
        section.setAttribute("id", methodDoc.getId());
        section.setAttribute("role", "detail");

        Element title = document.createElement("title");
        section.appendChild(title);
        title.appendChild(linkRenderer.link(methodDoc.getMetaData().getReturnType(), listener));
        title.appendChild(document.createTextNode(" "));
        Element literal = document.createElement("literal");
        title.appendChild(literal);
        literal.appendChild(document.createTextNode(methodDoc.getName()));
        title.appendChild(document.createTextNode("("));
        List<ParameterMetaData> parameters = methodDoc.getMetaData().getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            ParameterMetaData param = parameters.get(i);
            if (i > 0) {
                title.appendChild(document.createTextNode(", "));
            }
            title.appendChild(linkRenderer.link(param.getType(), listener));
            title.appendChild(document.createTextNode(" " + param.getName()));
        }
        title.appendChild(document.createTextNode(")"));

        warningsRenderer.renderTo(methodDoc, "method", section);

        for (Element element : methodDoc.getComment()) {
            section.appendChild(document.importNode(element, true));
        }
    }
}
