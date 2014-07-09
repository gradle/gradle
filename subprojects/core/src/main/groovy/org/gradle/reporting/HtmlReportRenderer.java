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
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.html.SimpleHtmlWriter;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

public class HtmlReportRenderer {
    public <T> void render(T model, ReportRenderer<T, HtmlReportContext<SimpleHtmlWriter>> renderer, final File outputDirectory) {
        try {
            outputDirectory.mkdirs();
            DefaultHtmlReportContext context = new DefaultHtmlReportContext(outputDirectory);
            renderer.render(model, context);
            for (URL resource : context.resources) {
                String name = StringUtils.substringAfterLast(resource.getPath(), "/");
                String type = StringUtils.substringAfterLast(resource.getPath(), ".");
                File destFile = new File(outputDirectory, String.format("%s/%s", type, name));
                if (!destFile.exists()) {
                    GFileUtils.copyURLToFile(resource, destFile);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class DefaultHtmlReportContext extends HtmlReportContext<SimpleHtmlWriter> {
        private final File outputDirectory;
        private final Set<URL> resources = new HashSet<URL>();

        public DefaultHtmlReportContext(File outputDirectory) {
            this.outputDirectory = outputDirectory;
        }

        @Override
        public void requireResource(URL resource) {
            resources.add(resource);
        }

        @Override
        public <T> void renderPage(String name, final T model, final ReportRenderer<T, SimpleHtmlWriter> renderer) {
            File outputFile = new File(outputDirectory, name);
            IoActions.writeTextFile(outputFile, "utf-8", new ErroringAction<Writer>() {
                @Override
                protected void doExecute(Writer writer) throws Exception {
                    renderer(renderer).render(model, writer);
                }
            });
        }

        private <T> ReportRenderer<T, Writer> renderer(final ReportRenderer<T, SimpleHtmlWriter> renderer) {
            return new ReportRenderer<T, Writer>() {
                @Override
                public void render(T model, Writer writer) throws IOException {
                    SimpleHtmlWriter htmlWriter = new SimpleHtmlWriter(writer, "");
                    htmlWriter.startElement("html");
                    renderer.render(model, htmlWriter);
                    htmlWriter.endElement();
                }
            };
        }
    }
}