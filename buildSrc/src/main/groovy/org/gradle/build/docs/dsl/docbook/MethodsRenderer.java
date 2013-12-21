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
import org.gradle.build.docs.dsl.docbook.model.MethodDoc;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collection;

public class MethodsRenderer {
    private final MethodTableRenderer methodTableRenderer = new MethodTableRenderer();
    private final ExtensionMethodsSummaryRenderer extensionMethodsSummaryRenderer;
    private final MethodDetailRenderer methodDetailRenderer;

    public MethodsRenderer(LinkRenderer linkRenderer, GenerationListener listener) {
        methodDetailRenderer = new MethodDetailRenderer(linkRenderer, listener);
        extensionMethodsSummaryRenderer = new ExtensionMethodsSummaryRenderer(methodTableRenderer);
    }

    public void renderTo(ClassDoc classDoc, Element parent) {
        Document document = parent.getOwnerDocument();

        Element summarySection = document.createElement("section");
        parent.appendChild(summarySection);

        Element title = document.createElement("title");
        summarySection.appendChild(title);
        title.appendChild(document.createTextNode("Methods"));

        boolean hasMethods = false;
        Collection<MethodDoc> classMethods = classDoc.getClassMethods();
        if (!classMethods.isEmpty()) {
            hasMethods = true;

            Element table = document.createElement("table");
            summarySection.appendChild(table);

            title = document.createElement("title");
            table.appendChild(title);
            title.appendChild(document.createTextNode("Methods - " + classDoc.getSimpleName()));

            methodTableRenderer.renderTo(classMethods, table);
        }

        for (ClassExtensionDoc extensionDoc : classDoc.getClassExtensions()) {
            hasMethods |= !extensionDoc.getExtensionMethods().isEmpty();
            extensionMethodsSummaryRenderer.renderTo(extensionDoc, summarySection);
        }

        if (!hasMethods) {
            Element para = document.createElement("para");
            summarySection.appendChild(para);
            para.appendChild(document.createTextNode("No methods"));
            return;
        }


        Element detailsSection = document.createElement("section");
        parent.appendChild(detailsSection);

        title = document.createElement("title");
        detailsSection.appendChild(title);
        title.appendChild(document.createTextNode("Method details"));

        for (MethodDoc methodDoc : classMethods) {
            methodDetailRenderer.renderTo(methodDoc, detailsSection);
        }
        for (ClassExtensionDoc extensionDoc : classDoc.getClassExtensions()) {
            for (MethodDoc methodDoc : extensionDoc.getExtensionMethods()) {
                methodDetailRenderer.renderTo(methodDoc, detailsSection);
            }
        }
    }
}
