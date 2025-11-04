/*
 * Copyright 2025 the original author or authors.
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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import org.apache.commons.io.file.PathUtils;
import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.testing.TestReportGenerator;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
import org.gradle.api.internal.tasks.testing.results.serializable.TestOutputReader;
import org.gradle.api.internal.tasks.testing.worker.TestEventSerializer;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.SafeFileLocationUtils;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.reporting.HtmlReportBuilder;
import org.gradle.reporting.HtmlReportRenderer;
import org.gradle.reporting.ReportRenderer;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates an HTML report based on test results based on binary results from {@link SerializableTestResultStore}.
 *
 * <p>
 * The root results are recorded into `index.html`, and then each parent tells its children to generate starting at `{childName}/index.html`.
 */
public abstract class GenericHtmlTestReportGenerator implements TestReportGenerator {

    private static final Logger LOG = Logging.getLogger(GenericHtmlTestReportGenerator.class);

    public static String getFilePath(org.gradle.util.Path path) {
        String filePath;
        if (path.segmentCount() == 0) {
            filePath = "index.html";
        } else {
            filePath = String.join("/", Iterables.transform(path.segments(), SafeFileLocationUtils::toSafeFileName)) + "/index.html";
        }
        return filePath;
    }
    private final BuildOperationRunner buildOperationRunner;
    private final BuildOperationExecutor buildOperationExecutor;
    private final MetadataRendererRegistry metadataRendererRegistry;
    private final Path reportsDirectory;

    @Inject
    public GenericHtmlTestReportGenerator(
        BuildOperationRunner buildOperationRunner,
        BuildOperationExecutor buildOperationExecutor,
        MetadataRendererRegistry metadataRendererRegistry,
        Path reportsDirectory
    ) {
        this.buildOperationRunner = buildOperationRunner;
        this.buildOperationExecutor = buildOperationExecutor;
        this.metadataRendererRegistry = metadataRendererRegistry;
        this.reportsDirectory = reportsDirectory;
    }

    @Override
    public Path generate(List<Path> resultsDirectories) {
        List<SerializableTestResultStore> stores = resultsDirectories.stream()
            .distinct()
            .map(SerializableTestResultStore::new)
            .filter(SerializableTestResultStore::hasResults)
            .collect(Collectors.toList());

        try {
            Files.createDirectories(reportsDirectory);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        Serializer<TestOutputEvent> testOutputEventSerializer = TestEventSerializer.create().build(TestOutputEvent.class);
        List<TestOutputReader> outputReaders = new ArrayList<>(stores.size());
        try {
            for (SerializableTestResultStore store : stores) {
                outputReaders.add(store.createOutputReader(testOutputEventSerializer));
            }

            TestTreeModel root = TestTreeModel.loadModelFromStores(stores);
            generateReport(root, outputReaders);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            CompositeStoppable.stoppable(outputReaders).stop();
        }
        return reportsDirectory.resolve("index.html");
    }

    private void generateReport(TestTreeModel root, List<TestOutputReader> outputReaders) {
        LOG.info("Generating HTML test report...");

        Timer clock = Time.startTimer();
        generateFiles(root, outputReaders);
        LOG.info("Finished generating test html results ({}) into: {}", clock.getElapsed(), reportsDirectory);
    }

    private void generateFiles(TestTreeModel model, final List<TestOutputReader> outputReaders) {
        try {
            HtmlReportRenderer htmlRenderer = new HtmlReportRenderer();
            buildOperationRunner.run(new DeleteOldReportOperation(reportsDirectory));

            ListMultimap<String, Integer> namesToIndexes = ArrayListMultimap.create();
            List<String> rootDisplayNames = new ArrayList<>(model.getPerRootInfo().size());
            for (int i = 0; i < model.getPerRootInfo().size(); i++) {
                // Roots should always have exactly one PerRootInfo entry
                List<TestTreeModel.PerRootInfo> perRootInfos = model.getPerRootInfo().get(i);
                if (perRootInfos.isEmpty()) {
                    throw new IllegalStateException("Root model is missing display name info for root index " + i);
                }
                if (perRootInfos.size() > 1) {
                    throw new IllegalStateException("Root model has multiple display name infos for root index " + i + ": " + Iterables.toString(perRootInfos));
                }
                String displayName = perRootInfos.get(0).getResult().getDisplayName();
                rootDisplayNames.add(displayName);
                namesToIndexes.put(displayName, i);
            }
            Multimaps.asMap(namesToIndexes).forEach((name, indexes) -> {
                if (indexes.size() > 1) {
                    // Rename affected roots to avoid conflicts: name (1), name (2), etc.
                    for (int nameRepeatIndex = 0; nameRepeatIndex < indexes.size(); nameRepeatIndex++) {
                        int rootIndex = indexes.get(nameRepeatIndex);
                        rootDisplayNames.set(rootIndex, name + " (" + (nameRepeatIndex + 1) + ")");
                    }
                }
            });

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
                    for (String child : tree.getChildren().keySet()) {
                        queueTree(queue, tree.getChildren().get(child), output);
                    }
                }
            }, reportsDirectory.toFile());
        } catch (Exception e) {
            throw new GradleException(String.format("Could not generate test report to '%s'.", reportsDirectory), e);
        }
    }

    private static final class HtmlReportFileGenerator implements RunnableBuildOperation {
        private final String fileUrl;
        private final TestTreeModel results;
        private final HtmlReportBuilder output;
        private final List<TestOutputReader> outputReaders;
        private final List<String> rootDisplayNames;
        private final MetadataRendererRegistry metadataRendererRegistry;

        HtmlReportFileGenerator(
            String fileUrl,
            TestTreeModel results,
            HtmlReportBuilder output,
            List<TestOutputReader> outputReaders,
            List<String> rootDisplayNames,
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

    private static final class DeleteOldReportOperation implements RunnableBuildOperation {
        private final Path reportsDirectory;

        private DeleteOldReportOperation(Path reportsDirectory) {
            this.reportsDirectory = reportsDirectory;
        }

        @Override
        public void run(BuildOperationContext context) {
            // Clean-up old HTML report
            Path indexHtml = reportsDirectory.resolve("index.html");
            try {
                PathUtils.deleteFile(indexHtml);
            } catch (IOException e) {
                LOG.info("Could not delete HTML test reports index.html '{}'.", indexHtml, e);
            }
            // Delete all directories, but not files, in the reports directory
            // This avoids deleting files from other report types that may be in the same directory
            try (Stream<Path> children = Files.list(reportsDirectory)) {
                for (Path dir : children.filter(Files::isDirectory).collect(Collectors.toList())) {
                    PathUtils.deleteDirectory(dir);
                }
            } catch (IOException e) {
                LOG.info("Could not clean HTML test reports directory '{}'.", reportsDirectory, e);
            }
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Delete old generic HTML results");
        }
    }
}
