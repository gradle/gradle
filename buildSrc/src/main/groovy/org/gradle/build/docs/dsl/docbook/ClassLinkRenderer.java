/*
 * Copyright 2010 the original author or authors.
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

import org.apache.commons.lang.StringUtils;
import org.gradle.build.docs.dsl.model.TypeMetaData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ClassLinkRenderer {
    private final Document document;
    private final DslDocModel model;

    public ClassLinkRenderer(Document document, DslDocModel model) {
        this.document = document;
        this.model = model;
    }

    Node link(TypeMetaData type) {
        final Element linkElement = document.createElement("classname");

        type.visitSignature(new TypeMetaData.SignatureVisitor() {
            public void visitText(String text) {
                linkElement.appendChild(document.createTextNode(text));
            }

            public void visitType(String name) {
                linkElement.appendChild(addType(name));
            }
        });

        linkElement.normalize();
        if (linkElement.getChildNodes().getLength() == 1 && linkElement.getFirstChild() instanceof Element) {
            return linkElement.getFirstChild();
        }
        return linkElement;
    }

    private Node addType(String className) {
        if (model.isKnownType(className)) {
            Element linkElement = document.createElement("apilink");
            linkElement.setAttribute("class", className);
            return linkElement;
        } else if (className.startsWith("java.")) {
            Element linkElement = document.createElement("ulink");
            linkElement.setAttribute("url", String.format("http://download.oracle.com/javase/1.5.0/docs/api/%s.html",
                    className.replace(".", "/")));
            Element classNameElement = document.createElement("classname");
            classNameElement.appendChild(document.createTextNode(StringUtils.substringAfterLast(className, ".")));
            linkElement.appendChild(classNameElement);
            return linkElement;
        } else if (className.startsWith("groovy.")) {
            Element linkElement = document.createElement("ulink");
            linkElement.setAttribute("url", String.format("http://groovy.codehaus.org/gapi/%s.html", className.replace(
                    ".", "/")));
            Element classNameElement = document.createElement("classname");
            classNameElement.appendChild(document.createTextNode(StringUtils.substringAfterLast(className, ".")));
            linkElement.appendChild(classNameElement);
            return linkElement;
        } else {
            return document.createTextNode(className);
        }
    }
}
