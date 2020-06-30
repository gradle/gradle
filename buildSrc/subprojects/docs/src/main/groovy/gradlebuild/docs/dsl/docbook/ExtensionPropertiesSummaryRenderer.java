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

import gradlebuild.docs.dsl.docbook.model.ClassExtensionDoc;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class ExtensionPropertiesSummaryRenderer {
    private final PropertyTableRenderer propertyTableRenderer;

    public ExtensionPropertiesSummaryRenderer(PropertyTableRenderer propertyTableRenderer) {
        this.propertyTableRenderer = propertyTableRenderer;
    }

    public void renderTo(ClassExtensionDoc extension, Element parent) {
        if (extension.getExtensionProperties().isEmpty()) {
            return;
        }

        Document document = parent.getOwnerDocument();

        Element section = document.createElement("section");
        parent.appendChild(section);

        Element title = document.createElement("title");
        section.appendChild(title);
        title.appendChild(document.createTextNode("Properties added by the "));
        Element literal = document.createElement("literal");
        title.appendChild(literal);
        literal.appendChild(document.createTextNode(extension.getPluginId()));
        title.appendChild(document.createTextNode(" plugin"));

        Element titleabbrev = document.createElement("titleabbrev");
        section.appendChild(titleabbrev);
        literal = document.createElement("literal");
        titleabbrev.appendChild(literal);
        literal.appendChild(document.createTextNode(extension.getPluginId()));
        titleabbrev.appendChild(document.createTextNode(" plugin"));

        Element table = document.createElement("table");
        section.appendChild(table);

        title = document.createElement("title");
        table.appendChild(title);
        title.appendChild(document.createTextNode("Properties - "));
        literal = document.createElement("literal");
        title.appendChild(literal);
        literal.appendChild(document.createTextNode(extension.getPluginId()));
        title.appendChild(document.createTextNode(" plugin"));

        propertyTableRenderer.renderTo(extension.getExtensionProperties(), table);
    }
}
