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
import java.util.HashMap;
import java.util.Map;

public class HtmlReportRenderer {
    public <T> void render(T model, ReportRenderer<T, HtmlReportBuilder<SimpleHtmlWriter>> renderer, File outputDirectory) {
        try {
            outputDirectory.mkdirs();
            DefaultHtmlReportContext context = new DefaultHtmlReportContext(outputDirectory);
            renderer.render(model, context);
            for (Resource resource : context.resources.values()) {
                File destFile = new File(outputDirectory, resource.path);
                if (!destFile.exists()) {
                    GFileUtils.copyURLToFile(resource.source, destFile);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class Resource {
        final URL source;
        final String path;

        private Resource(URL source, String path) {
            this.source = source;
            this.path = path;
        }
    }

    private static class DefaultHtmlReportContext implements HtmlReportBuilder<SimpleHtmlWriter> {
        private final File outputDirectory;
        private final Map<String, Resource> resources = new HashMap<String, Resource>();

        public DefaultHtmlReportContext(File outputDirectory) {
            this.outputDirectory = outputDirectory;
        }

        Resource addResource(URL source) {
            String name = StringUtils.substringAfterLast(source.getPath(), "/");
            String type = StringUtils.substringAfterLast(source.getPath(), ".");
            String path = String.format("%s/%s", type, name);
            Resource resource = resources.get(path);
            if (resource == null) {
                resource = new Resource(source, path);
                resources.put(path, resource);
            }
            return resource;
        }

        public void requireResource(URL source) {
            addResource(source);
        }

        public <T> void renderPage(String name, final T model, final ReportRenderer<T, SimpleHtmlWriter> renderer) {
            File outputFile = new File(outputDirectory, name);
            IoActions.writeTextFile(outputFile, "utf-8", new ErroringAction<Writer>() {
                @Override
                protected void doExecute(Writer writer) throws Exception {
                    renderer(renderer).render(model, writer);
                }
            });
        }

        public <T> void render(final String name, T model, final ReportRenderer<T, HtmlPageBuilder<SimpleHtmlWriter>> renderer) {
            renderPage(name, model, new ReportRenderer<T, SimpleHtmlWriter>() {
                @Override
                public void render(T model, SimpleHtmlWriter output) throws IOException {
                    renderer.render(model, new DefaultHtmlPageBuilder(prefix(name), output));
                }
            });
        }

        private String prefix(String name) {
            StringBuilder builder = new StringBuilder();
            int pos = 0;
            while (pos < name.length()) {
                int next = name.indexOf('/', pos);
                if (next < 0) {
                    break;
                }
                builder.append("../");
                pos = next + 1;
            }
            return builder.toString();
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

        private class DefaultHtmlPageBuilder implements HtmlPageBuilder<SimpleHtmlWriter> {
            private final String prefix;
            private final SimpleHtmlWriter output;

            public DefaultHtmlPageBuilder(String prefix, SimpleHtmlWriter output) {
                this.prefix = prefix;
                this.output = output;
            }

            public String requireResource(URL source) {
                Resource resource = addResource(source);
                return prefix + resource.path;
            }

            public SimpleHtmlWriter getOutput() {
                return output;
            }
        }
    }
}