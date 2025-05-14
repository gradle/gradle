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
import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;
import org.gradle.internal.html.SimpleHtmlWriter;
import org.gradle.util.internal.GFileUtils;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class HtmlReportRenderer {
    /**
     * Renders a multi-page HTML report from the given model, into the given directory.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public <T> void render(T model, ReportRenderer<T, HtmlReportBuilder> renderer, File outputDirectory) {
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

    /**
     * Renders a single page HTML report from the given model, into the given output file.
     */
    public <T> void renderSinglePage(T model, final ReportRenderer<T, HtmlPageBuilder<SimpleHtmlWriter>> renderer, final File outputFile) {
        render(model, new ReportRenderer<T, HtmlReportBuilder>() {
            @Override
            public void render(T model, HtmlReportBuilder output) {
                output.renderHtmlPage(outputFile.getName(), model, renderer);
            }
        }, outputFile.getParentFile());
    }

    /**
     * Renders a single page HTML report from the given model, into the given output file.
     */
    public <T> void renderRawSinglePage(T model, final ReportRenderer<T, HtmlPageBuilder<Writer>> renderer, final File outputFile) {
        render(model, new ReportRenderer<T, HtmlReportBuilder>() {
            @Override
            public void render(T model, HtmlReportBuilder output) {
                output.renderRawHtmlPage(outputFile.getName(), model, renderer);
            }
        }, outputFile.getParentFile());
    }

    private static class Resource {
        final URL source;
        final String path;

        private Resource(URL source, String path) {
            this.source = source;
            this.path = path;
        }
    }

    private static class DefaultHtmlReportContext implements HtmlReportBuilder {
        private final File outputDirectory;
        private final Map<String, Resource> resources = new HashMap<>();

        public DefaultHtmlReportContext(File outputDirectory) {
            this.outputDirectory = outputDirectory;
        }

        Resource addResource(URL source) {
            String urlString = source.toString();
            Resource resource = resources.get(urlString);
            if (resource == null) {
                String name = StringUtils.substringAfterLast(source.getPath(), "/");
                String type = StringUtils.substringAfterLast(source.getPath(), ".");
                if (type.equalsIgnoreCase("png") || type.equalsIgnoreCase("gif")) {
                    type = "images";
                }
                String path = type + "/" + name;
                resource = new Resource(source, path);
                resources.put(urlString, resource);
            }
            return resource;
        }

        @Override
        public void requireResource(URL source) {
            addResource(source);
        }

        @Override
        public <T> void renderHtmlPage(final String name, final T model, final ReportRenderer<T, HtmlPageBuilder<SimpleHtmlWriter>> renderer) {
            File outputFile = new File(outputDirectory, name);
            IoActions.writeTextFile(outputFile, "utf-8", new ErroringAction<Writer>() {
                @Override
                protected void doExecute(@NonNull Writer writer) throws Exception {
                    SimpleHtmlWriter htmlWriter = new SimpleHtmlWriter(writer, "");
                    htmlWriter.startElement("html");
                    renderer.render(model, new DefaultHtmlPageBuilder<>(prefix(name), htmlWriter));
                    htmlWriter.endElement();
                }
            });
        }

        @Override
        public <T> void renderRawHtmlPage(final String name, final T model, final ReportRenderer<T, HtmlPageBuilder<Writer>> renderer) {
            File outputFile = new File(outputDirectory, name);
            IoActions.writeTextFile(outputFile, "utf-8", new ErroringAction<Writer>() {
                @Override
                protected void doExecute(@NonNull Writer writer) throws Exception {
                    renderer.render(model, new DefaultHtmlPageBuilder<>(prefix(name), writer));
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

        private class DefaultHtmlPageBuilder<D> implements HtmlPageBuilder<D> {
            private final String prefix;
            private final D output;

            public DefaultHtmlPageBuilder(String prefix, D output) {
                this.prefix = prefix;
                this.output = output;
            }

            @Override
            public String requireResource(URL source) {
                Resource resource = addResource(source);
                return prefix + resource.path;
            }

            @Override
            public String formatDate(Date date) {
                return DateFormat.getDateTimeInstance().format(date);
            }

            @Override
            public D getOutput() {
                return output;
            }
        }
    }
}
