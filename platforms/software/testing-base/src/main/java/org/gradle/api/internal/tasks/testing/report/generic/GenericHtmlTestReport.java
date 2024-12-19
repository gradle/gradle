/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.testing.report.HtmlTestReport;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.reporting.HtmlReportBuilder;
import org.gradle.reporting.HtmlReportRenderer;
import org.gradle.reporting.ReportRenderer;
import org.gradle.util.Path;
import org.gradle.util.internal.GFileUtils;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Generates an HTML report based on test results from an {@link TestTreeModel}.
 *
 * <p>
 * Unlike {@link HtmlTestReport}, this report does not assume that the test results are from JUnit tests. They may even be non-JVM tests.
 * </p>
 *
 * <p>
 * The root results are recorded into `index.html`, and then each parent tells its children to generate starting at `{childName}/index.html`.
 */
public class GenericHtmlTestReport {

    private static final Logger LOG = Logging.getLogger(GenericHtmlTestReport.class);

    public static String getFilePath(Path path) {
        String filePath;
        if (path.segmentCount() == 0) {
            filePath = "index.html";
        } else {
            filePath = String.join("/", path.segments()) + "/index.html";
        }
        return filePath;
    }

    private final BuildOperationRunner buildOperationRunner;
    private final BuildOperationExecutor buildOperationExecutor;
    private final Map<String, SerializableTestResultStore.OutputReader> outputReaders;
    private final MetadataRendererRegistry metadataRendererRegistry;

    public GenericHtmlTestReport(
        BuildOperationRunner buildOperationRunner,
        BuildOperationExecutor buildOperationExecutor,
        Map<String, SerializableTestResultStore.OutputReader> outputReaders,
        MetadataRendererRegistry metadataRendererRegistry
    ) {
        this.buildOperationRunner = buildOperationRunner;
        this.buildOperationExecutor = buildOperationExecutor;
        this.outputReaders = outputReaders;
        this.metadataRendererRegistry = metadataRendererRegistry;
    }

    public void generateReport(TestTreeModel root, java.nio.file.Path reportDir) {
        LOG.info("Generating HTML test report...");

        Timer clock = Time.startTimer();
        generateFiles(root, reportDir);
        LOG.info("Finished generating test html results ({}) into: {}", clock.getElapsed(), reportDir);
    }

    private void generateFiles(TestTreeModel model, final java.nio.file.Path reportDir) {
        try {
            HtmlReportRenderer htmlRenderer = new HtmlReportRenderer();
            buildOperationRunner.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    // Clean-up old HTML report directories
                    GFileUtils.deleteQuietly(reportDir.toFile());
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Delete old generic HTML results");
                }
            });

            Map<String, String> rootDisplayNames = new HashMap<>(model.getPerRootInfo().size());
            for (Map.Entry<String, TestTreeModel.PerRootInfo> entry : model.getPerRootInfo().entrySet()) {
                rootDisplayNames.put(entry.getKey(), entry.getValue().getResult().getDisplayName());
            }

            htmlRenderer.render(model, new ReportRenderer<TestTreeModel, HtmlReportBuilder>() {
                @Override
                public void render(final TestTreeModel model, final HtmlReportBuilder output) {
                    buildOperationExecutor.runAll(queue -> {
                        queueTree(queue, model, output);
                    });
                }

                private void queueTree(BuildOperationQueue<RunnableBuildOperation> queue, TestTreeModel tree, HtmlReportBuilder output) {
                    String filePath = getFilePath(tree.getPath());
                    queue.add(new HtmlReportFileGenerator(
                        filePath,
                        tree,
                        output,
                        outputReaders,
                        rootDisplayNames,
                        metadataRendererRegistry
                    ));
                    Set<String> allChildren = new LinkedHashSet<>();
                    for (TestTreeModel.PerRootInfo perRootInfo : tree.getPerRootInfo().values()) {
                        allChildren.addAll(perRootInfo.getChildren());
                    }
                    for (String child : allChildren) {
                        queueTree(queue, tree.getChildren().get(child), output);
                    }
                }
            }, reportDir.toFile());
        } catch (Exception e) {
            throw new GradleException(String.format("Could not generate test report to '%s'.", reportDir), e);
        }
    }

    private static final class HtmlReportFileGenerator implements RunnableBuildOperation {
        private final String fileUrl;
        private final TestTreeModel results;
        private final HtmlReportBuilder output;
        private final Map<String, SerializableTestResultStore.OutputReader> outputReaders;
        private final Map<String, String> rootDisplayNames;
        private final MetadataRendererRegistry metadataRendererRegistry;

        HtmlReportFileGenerator(
            String fileUrl,
            TestTreeModel results,
            HtmlReportBuilder output,
            Map<String, SerializableTestResultStore.OutputReader> outputReaders,
            Map<String, String> rootDisplayNames,
            MetadataRendererRegistry metadataRendererRegistry
        ) {
            this.fileUrl = fileUrl;
            this.results = results;
            this.output = output;
            this.outputReaders = outputReaders;
            this.rootDisplayNames = rootDisplayNames;
            this.metadataRendererRegistry = metadataRendererRegistry;
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Generate generic HTML test report for " + results.getPath().getName());
        }

        @Override
        public void run(BuildOperationContext context) {
            output.renderHtmlPage(fileUrl, results, new GenericPageRenderer(outputReaders, rootDisplayNames, metadataRendererRegistry));
        }
    }
}
