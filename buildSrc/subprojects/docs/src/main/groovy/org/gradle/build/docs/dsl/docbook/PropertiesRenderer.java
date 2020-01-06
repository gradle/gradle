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

class PropertiesRenderer implements ClassDocMemberRenderer {
    private final PropertyTableRenderer propertyTableRenderer = new PropertyTableRenderer();
    private final ExtensionPropertiesSummaryRenderer extensionPropertiesSummaryRenderer;
    private final PropertyDetailRenderer propertiesDetailRenderer;

    public PropertiesRenderer(LinkRenderer linkRenderer, GenerationListener listener) {
        propertiesDetailRenderer = new PropertyDetailRenderer(linkRenderer, listener);
        extensionPropertiesSummaryRenderer = new ExtensionPropertiesSummaryRenderer(propertyTableRenderer);
    }

    @Override
    public void renderSummaryTo(ClassDoc classDoc, Element parent) {
        Document document = parent.getOwnerDocument();

        Element summarySection = document.createElement("section");
        parent.appendChild(summarySection);

        Element title = document.createElement("title");
        summarySection.appendChild(title);
        title.appendChild(document.createTextNode("Properties"));

        Collection<PropertyDoc> classProperties = classDoc.getClassProperties();
        if (!classProperties.isEmpty()) {

            Element table = document.createElement("table");
            summarySection.appendChild(table);

            title = document.createElement("title");
            table.appendChild(title);
            title.appendChild(document.createTextNode("Properties - " + classDoc.getSimpleName()));

            propertyTableRenderer.renderTo(classProperties, table);
        }

        for (ClassExtensionDoc extensionDoc : classDoc.getClassExtensions()) {
            extensionPropertiesSummaryRenderer.renderTo(extensionDoc, summarySection);
        }

        if (!hasProperties(classDoc)) {
            Element para = document.createElement("para");
            summarySection.appendChild(para);
            para.appendChild(document.createTextNode("No properties"));
        }
    }

    @Override
    public void renderDetailsTo(ClassDoc classDoc, Element parent) {
        if (hasProperties(classDoc)) {
            Document document = parent.getOwnerDocument();
            Element detailsSection = document.createElement("section");
            parent.appendChild(detailsSection);

            Element title = document.createElement("title");
            detailsSection.appendChild(title);
            title.appendChild(document.createTextNode("Property details"));

            for (PropertyDoc classProperty : classDoc.getClassProperties()) {
                propertiesDetailRenderer.renderTo(classProperty, detailsSection);
            }
            for (ClassExtensionDoc extensionDoc : classDoc.getClassExtensions()) {
                for (PropertyDoc propertyDoc : extensionDoc.getExtensionProperties()) {
                    propertiesDetailRenderer.renderTo(propertyDoc, detailsSection);
                }
            }
        }
    }

    private boolean hasProperties(ClassDoc classDoc) {
        boolean hasProperties = false;
        if (!classDoc.getClassProperties().isEmpty()) {
            hasProperties = true;
        }
        for (ClassExtensionDoc extensionDoc : classDoc.getClassExtensions()) {
            hasProperties |= !extensionDoc.getExtensionProperties().isEmpty();
        }
        return hasProperties;
    }

}
