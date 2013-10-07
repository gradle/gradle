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
import org.gradle.api.internal.html.SimpleHtmlWriter;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.Writer;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

public class HtmlReportRenderer {
    private final Set<URL> resources = new HashSet<URL>();

    public void requireResource(URL resource) {
        resources.add(resource);
    }

    public <T> TextReportRenderer<T> renderer(final ReportRenderer<T, SimpleHtmlWriter> renderer) {
        return renderer(new TextReportRenderer<T>() {
            @Override
            protected void writeTo(T model, Writer writer) throws Exception {
                SimpleHtmlWriter htmlWriter = new SimpleHtmlWriter(writer, "");
                htmlWriter.startElement("html");
                renderer.render(model, htmlWriter);
                htmlWriter.endElement();
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
                    String type = StringUtils.substringAfterLast(resource.getPath(), ".");
                    File destFile = new File(file.getParentFile(), String.format("%s/%s", type, name));
                    if (!destFile.exists()) {
                        destFile.getParentFile().mkdirs();
                        GFileUtils.copyURLToFile(resource, destFile);
                    }
                }
            }
        };
    }
}