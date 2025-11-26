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
package org.gradle.api.internal.tasks.testing.report.generic;

import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import com.google.common.net.UrlEscapers;
import org.gradle.api.internal.tasks.testing.DefaultTestFileAttachmentDataEvent;
import org.gradle.api.internal.tasks.testing.DefaultTestKeyValueDataEvent;
import org.gradle.api.internal.tasks.testing.results.serializable.OutputEntry;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResult;
import org.gradle.api.internal.tasks.testing.results.serializable.TestOutputReader;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.html.SimpleHtmlWriter;
import org.gradle.reporting.ReportRenderer;
import org.gradle.reporting.TabbedPageRenderer;
import org.gradle.reporting.TabsRenderer;
import org.gradle.util.Path;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class GenericPageRenderer extends TabbedPageRenderer<TestTreeModel> {
    private static final URL STYLE_URL = Resources.getResource(GenericPageRenderer.class, "style.css");

    public static String getUrlTo(
        Path originatingPath, boolean isOriginatingPathLeaf,
        Path targetPath, boolean isTargetPathLeaf
    ) {
        if (originatingPath.equals(targetPath) && isOriginatingPathLeaf == isTargetPathLeaf) {
            return "#";
        }
        // We know we're emitting to the file system, so let's just use NIO Path to do the path manipulation.
        // We need the `.` for relative resolution to work properly
        java.nio.file.Path relativePath = Paths.get("./" + GenericHtmlTestReportGenerator.getFilePath(originatingPath, isOriginatingPathLeaf)).getParent()
            .relativize(Paths.get("./" + GenericHtmlTestReportGenerator.getFilePath(targetPath, isTargetPathLeaf)));
        // Escape things that aren't `/` for the URL
        StringBuilder url = new StringBuilder();
        for (java.nio.file.Path segment : relativePath) {
            url.append(UrlEscapers.urlPathSegmentEscaper().escape(segment.toString()));
            url.append('/');
        }
        // Remove trailing `/`
        return url.substring(0, url.length() - 1);
    }

    private final List<TestOutputReader> outputReaders;
    private final List<String> rootDisplayNames;

    GenericPageRenderer(
        List<TestOutputReader> outputReaders,
        List<String> rootDisplayNames
    ) {
        this.outputReaders = outputReaders;
        this.rootDisplayNames = rootDisplayNames;
    }

    private void renderBreadcrumbs(SimpleHtmlWriter htmlWriter) throws IOException {
        Path path = getModel().getPath();
        if (path.equals(Path.ROOT)) {
            return;
        }
        htmlWriter.startElement("div").attribute("class", "breadcrumbs");
        for (Path ancestorPath : path.ancestors()) {
            String title = ancestorPath.equals(Path.ROOT) ? "all" : ancestorPath.getName();
            htmlWriter.startElement("a")
                .attribute("class", "breadcrumb")
                .attribute("href", getUrlTo(
                    path, getModel().getChildren().isEmpty(),
                    ancestorPath, false
                ))
                .characters(title)
                .endElement();
            htmlWriter.characters(" > ");
        }
        htmlWriter.startElement("span")
            .attribute("class", "breadcrumb")
            .characters(path.getName())
            .endElement();
        htmlWriter.endElement();
    }

    @Override
    protected URL getStyleUrl() {
        return STYLE_URL;
    }

    @Override
    protected String getTitle() {
        // Show "All Results" in the root, otherwise show nothing, the display name will be provided in each root.
        return buildTitle("All Results", name -> "");
    }

    @Override
    protected String getPageTitle() {
        return buildTitle("Test results - All Results", name -> "Test results - " + name);
    }

    private String buildTitle(String rootTitle, Function<String, String> buildTitleFromName) {
        String name = getModel().getPath().getName();
        if (name == null) {
            return rootTitle;
        }
        return buildTitleFromName.apply(name);
    }

    @Override
    protected ReportRenderer<TestTreeModel, SimpleHtmlWriter> getHeaderRenderer() {
        return new ReportRenderer<TestTreeModel, SimpleHtmlWriter>() {
            @Override
            public void render(TestTreeModel model, SimpleHtmlWriter htmlWriter) throws IOException {
                renderBreadcrumbs(htmlWriter);
            }
        };
    }

    @Override
    protected ReportRenderer<TestTreeModel, SimpleHtmlWriter> getContentRenderer() {
        TabsRenderer<TestTreeModel> rootTabsRenderer = new TabsRenderer<>();
        List<List<PerRootInfo>> perRootInfo = getModel().getPerRootInfo();
        for (int rootIndex = 0; rootIndex < perRootInfo.size(); rootIndex++) {
            List<PerRootInfo> infos = perRootInfo.get(rootIndex);
            if (infos.isEmpty()) {
                continue;
            }
            List<TabsRenderer<TestTreeModel>> perRootInfoTabsRenderers = new ArrayList<>(infos.size());
            for (int perRootInfoIndex = 0; perRootInfoIndex < infos.size(); perRootInfoIndex++) {
                PerRootInfo info = infos.get(perRootInfoIndex);

                final TabsRenderer<TestTreeModel> perRootInfoTabsRenderer = new TabsRenderer<>();
                perRootInfoTabsRenderer.add("summary", new PerRootTabRenderer.ForSummary(rootIndex, perRootInfoIndex));
                TestOutputReader outputReader = outputReaders.get(rootIndex);
                boolean hasStdout = false;
                boolean hasStderr = false;
                for (OutputEntry outputEntry : info.getOutputEntries()) {
                    if (outputReader.hasOutput(outputEntry, TestOutputEvent.Destination.StdOut)) {
                        hasStdout = true;
                    }
                    if (outputReader.hasOutput(outputEntry, TestOutputEvent.Destination.StdErr)) {
                        hasStderr = true;
                    }

                    // Early exit if we have both
                    if (hasStdout && hasStderr) {
                        break;
                    }
                }
                if (hasStdout) {
                    perRootInfoTabsRenderer.add("standard output", new PerRootTabRenderer.ForOutput(rootIndex, perRootInfoIndex, outputReader, TestOutputEvent.Destination.StdOut));
                }
                if (hasStderr) {
                    perRootInfoTabsRenderer.add("error output", new PerRootTabRenderer.ForOutput(rootIndex, perRootInfoIndex, outputReader, TestOutputEvent.Destination.StdErr));
                }
                // TODO: This should be handled differently so that the renders know what to expect vs the GenericPageRenderer doing something special
                if (Iterables.any(info.getMetadatas(), event -> event instanceof DefaultTestKeyValueDataEvent)) {
                    perRootInfoTabsRenderer.add("data", new PerRootTabRenderer.ForKeyValues(rootIndex, perRootInfoIndex));
                }
                if (Iterables.any(info.getMetadatas(), event -> event instanceof DefaultTestFileAttachmentDataEvent)) {
                    perRootInfoTabsRenderer.add("attachments", new PerRootTabRenderer.ForFileAttachments(rootIndex, perRootInfoIndex));
                }

                perRootInfoTabsRenderers.add(perRootInfoTabsRenderer);
            }

            // If necessary, render each run in its own tab
            TabsRenderer<TestTreeModel> directlyBelowRootTabsRenderer;
            if (perRootInfoTabsRenderers.size() == 1) {
                directlyBelowRootTabsRenderer = perRootInfoTabsRenderers.get(0);
            } else {
                directlyBelowRootTabsRenderer = new TabsRenderer<>();
                for (int i = 0; i < perRootInfoTabsRenderers.size(); i++) {
                    directlyBelowRootTabsRenderer.add("run " + (i + 1), perRootInfoTabsRenderers.get(i));
                }
            }
            rootTabsRenderer.add(rootDisplayNames.get(rootIndex), new ReportRenderer<TestTreeModel, SimpleHtmlWriter>() {
                @Override
                public void render(TestTreeModel model, SimpleHtmlWriter output) throws IOException {
                    String displayName = SerializableTestResult.getCombinedDisplayName(infos.get(0).getResults());
                    output.startElement("h1").characters(displayName).endElement();
                    directlyBelowRootTabsRenderer.render(model, output);
                }
            });
        }
        return rootTabsRenderer;
    }
}
