/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.reporting;

import org.gradle.internal.UncheckedException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;

public class TextDomReportRenderer<T> extends DomReportRenderer<T> {
    private final TextReportRenderer<T> renderer;

    public TextDomReportRenderer(TextReportRenderer<T> renderer) {
        this.renderer = renderer;
    }

    @Override
    public void render(T model, Element parent) {
        try {
            StringWriter writer = new StringWriter();
            renderer.writeTo(model, writer);
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(writer.toString().getBytes()));
            NodeList children = document.getDocumentElement().getChildNodes();

            for (int i = 0; i < children.getLength(); i++) {
                Node adopted = parent.getOwnerDocument().importNode(children.item(i), true);
                parent.appendChild(adopted);
            }
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
