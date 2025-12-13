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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import org.apache.commons.io.file.PathUtils;
import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.testing.TestReportGenerator;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResult;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
import org.gradle.api.internal.tasks.testing.results.serializable.TestOutputReader;
import org.gradle.api.internal.tasks.testing.worker.TestEventSerializer;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.SafeFileLocationUtils;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.operations.BuildOperationConstraint;
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
import java.util.Objects;
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

    public static String getFilePath(org.gradle.util.Path path, boolean isLeaf) {
        String filePath;
        if (path.segmentCount() == 0) {
            filePath = "index.html";
        } else if (isLeaf && !Objects.equals(path.getName(), "index")) {
            // Avoid using a directory for each leaf node unless its name clashes (i.e. "index")
            // This reduces VFS overhead from many directories for large test suites
            String prefix = String.join("/", Iterables.transform(
                path.getParent().segments(),
                name -> SafeFileLocationUtils.toSafeFileName(name, true)
            ));
            filePath = prefix + (prefix.isEmpty() ? "" : "/") + SafeFileLocationUtils.toSafeFileName(path.getName() + ".html", false);
        } else {
            filePath = String.join("/", Iterables.transform(
                path.segments(),
                name -> SafeFileLocationUtils.toSafeFileName(name, true)
            )) + "/index.html";
        }
        return filePath;
    }

    private static String getFilePath(TestTreeModel tree) {
        return getFilePath(tree.getPath(), tree.getChildren().isEmpty());
    }

    private final BuildOperationRunner buildOperationRunner;
    private final BuildOperationExecutor buildOperationExecutor;
    private final Path reportsDirectory;

    @Inject
    public GenericHtmlTestReportGenerator(
        BuildOperationRunner buildOperationRunner,
        BuildOperationExecutor buildOperationExecutor,
        Path reportsDirectory
    ) {
        this.buildOperationRunner = buildOperationRunner;
        this.buildOperationExecutor = buildOperationExecutor;
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
        } catch (Exception e) {
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
                String displayName = getDisplayName(model, i);
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
                    buildOperationExecutor.runAll(
                        queue -> queueTree(queue, model, output),
                        // This is mostly I/O, so run this in UNCONSTRAINED mode to allow more parallelism
                        BuildOperationConstraint.UNCONSTRAINED
                    );
                }

                private void queueTree(BuildOperationQueue<RunnableBuildOperation> queue, TestTreeModel tree, HtmlReportBuilder output) {
                    ImmutableList.Builder<TestTreeModel> requestsBuilder = ImmutableList.builder();
                    requestsBuilder.add(tree);

                    for (TestTreeModel childTree : tree.getChildren()) {
                        // A container also emits all of its leaf children
                        if (childTree.getChildren().isEmpty()) {
                            requestsBuilder.add(childTree);
                        } else {
                            queueTree(queue, childTree, output);
                        }
                    }
                    queue.add(new HtmlReportFileGenerator(
                        requestsBuilder.build(),
                        output,
                        outputReaders,
                        rootDisplayNames
                    ));
                }
            }, reportsDirectory.toFile());
        } catch (Exception e) {
            throw new GradleException(String.format("Could not generate test report to '%s'.", reportsDirectory), e);
        }
    }

    private static String getDisplayName(TestTreeModel model, int rootIndex) {
        List<PerRootInfo> perRootInfos = model.getPerRootInfo().get(rootIndex);
        if (perRootInfos.isEmpty()) {
            throw new IllegalStateException("Root model is missing display name info for root index " + rootIndex);
        }
        if (perRootInfos.size() > 1) {
            throw new IllegalStateException("Root model has multiple display name infos for root index " + rootIndex + ": " + Iterables.toString(perRootInfos));
        }
        return SerializableTestResult.getCombinedDisplayName(perRootInfos.get(0).getResults());
    }

    private static final class HtmlReportFileGenerator implements RunnableBuildOperation {
        private final List<TestTreeModel> requests;
        private final HtmlReportBuilder output;
        private final List<TestOutputReader> outputReaders;
        private final List<String> rootDisplayNames;

        HtmlReportFileGenerator(
            List<TestTreeModel> requests,
            HtmlReportBuilder output,
            List<TestOutputReader> outputReaders,
            List<String> rootDisplayNames
        ) {
            this.requests = requests;
            this.output = output;
            this.outputReaders = outputReaders;
            this.rootDisplayNames = rootDisplayNames;
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName(
                "Generate generic HTML test report for " + requests.stream()
                    .map(r -> r.getPath().toString())
                    .collect(Collectors.joining(", "))
            );
        }

        @Override
        public void run(BuildOperationContext context) {
            GenericPageRenderer renderer = new GenericPageRenderer(outputReaders, rootDisplayNames);
            for (TestTreeModel request : requests) {
                output.renderHtmlPage(getFilePath(request), request, renderer);
            }
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
