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
import org.gradle.build.docs.dsl.docbook.model.ClassExtensionDoc;
import org.gradle.build.docs.dsl.docbook.model.PropertyDoc;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collection;

public class PropertiesRenderer {
    private final PropertyTableRenderer propertyTableRenderer = new PropertyTableRenderer();
    private final ExtensionPropertiesSummaryRenderer extensionPropertiesSummaryRenderer;
    private final PropertyDetailRenderer propertiesDetailRenderer;

    public PropertiesRenderer(LinkRenderer linkRenderer, GenerationListener listener) {
        propertiesDetailRenderer = new PropertyDetailRenderer(linkRenderer, listener);
        extensionPropertiesSummaryRenderer = new ExtensionPropertiesSummaryRenderer(propertyTableRenderer);
    }

    public void renderTo(ClassDoc classDoc, Element parent) {
        Document document = parent.getOwnerDocument();

        Element summarySection = document.createElement("section");
        parent.appendChild(summarySection);

        Element title = document.createElement("title");
        summarySection.appendChild(title);
        title.appendChild(document.createTextNode("Properties"));

        boolean hasProperties = false;
        Collection<PropertyDoc> classProperties = classDoc.getClassProperties();
        if (!classProperties.isEmpty()) {
            hasProperties = true;

            Element table = document.createElement("table");
            summarySection.appendChild(table);

            title = document.createElement("title");
            table.appendChild(title);
            title.appendChild(document.createTextNode("Properties - " + classDoc.getSimpleName()));

            propertyTableRenderer.renderTo(classProperties, table);
        }

        for (ClassExtensionDoc extensionDoc : classDoc.getClassExtensions()) {
            hasProperties |= !extensionDoc.getExtensionProperties().isEmpty();
            extensionPropertiesSummaryRenderer.renderTo(extensionDoc, summarySection);
        }

        if (!hasProperties) {
            Element para = document.createElement("para");
            summarySection.appendChild(para);
            para.appendChild(document.createTextNode("No properties"));
            return;
        }


        Element detailsSection = document.createElement("section");
        parent.appendChild(detailsSection);

        title = document.createElement("title");
        detailsSection.appendChild(title);
        title.appendChild(document.createTextNode("Property details"));

        for (PropertyDoc classProperty : classProperties) {
            propertiesDetailRenderer.renderTo(classProperty, detailsSection);
        }
        for (ClassExtensionDoc extensionDoc : classDoc.getClassExtensions()) {
            for (PropertyDoc propertyDoc : extensionDoc.getExtensionProperties()) {
                propertiesDetailRenderer.renderTo(propertyDoc, detailsSection);
            }
        }
    }
}
