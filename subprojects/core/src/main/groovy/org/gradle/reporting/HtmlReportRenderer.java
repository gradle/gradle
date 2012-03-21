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

import org.apache.commons.lang.StringUtils;
import org.gradle.internal.SystemProperties;
import org.gradle.util.GFileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

public class HtmlReportRenderer {
    private DocumentBuilder documentBuilder;
    private Transformer transformer;
    private final Set<URL> resources = new HashSet<URL>();

    public void requireResource(URL resource) {
        resources.add(resource);
    }

    public <T> TextReportRenderer<T> renderer(final DomReportRenderer<T> renderer) {
        return renderer(new TextReportRenderer<T>() {
            @Override
            protected void writeTo(T model, Writer writer) throws Exception {
                if (documentBuilder == null) {
                    documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                }
                Document document = documentBuilder.newDocument();

                Element html = document.createElement("html");
                document.appendChild(html);
                renderer.render(model, html);

                if (transformer == null) {
                    TransformerFactory factory = TransformerFactory.newInstance();
                    transformer = factory.newTransformer();
                    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                    transformer.setOutputProperty(OutputKeys.METHOD, "html");
                    transformer.setOutputProperty(OutputKeys.MEDIA_TYPE, "text/html");
                }

                writer.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">");
                writer.write(SystemProperties.getLineSeparator());
                transformer.transform(new DOMSource(document), new StreamResult(writer));
            }
        });
    }

    public <T> TextReportRenderer<T> renderer(final TextReportRenderer<T> renderer) {
        return new TextReportRenderer<T>() {
            @Override
            protected void writeTo(T model, Writer out) throws Exception {
                renderer.writeTo(model, out);
            }

            @Override
            public void writeTo(T model, File file) {
                super.writeTo(model, file);
                for (URL resource : resources) {
                    String name = StringUtils.substringAfterLast(resource.getPath(), "/");
                    File destFile = new File(file.getParentFile(), name);
                    if (!destFile.exists()) {
                        GFileUtils.copyURLToFile(resource, destFile);
                    }
                }
            }
        };
    }
}