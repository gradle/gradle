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

import org.gradle.api.internal.tasks.testing.TestReportGenerator;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates HTML test reports given binary test results.
 */
public class GenericHtmlTestReportGenerator implements TestReportGenerator {

    @ServiceScope(Scope.BuildSession.class)
    public static class Factory {
        private final BuildOperationRunner buildOperationRunner;
        private final BuildOperationExecutor buildOperationExecutor;
        private final MetadataRendererRegistry metadataRendererRegistry;

        @Inject
        public Factory(
            BuildOperationRunner buildOperationRunner,
            BuildOperationExecutor buildOperationExecutor,
            MetadataRendererRegistry metadataRendererRegistry
        ) {
            this.buildOperationRunner = buildOperationRunner;
            this.buildOperationExecutor = buildOperationExecutor;
            this.metadataRendererRegistry = metadataRendererRegistry;
        }

        public GenericHtmlTestReportGenerator create(Path reportsDirectory) {
            return new GenericHtmlTestReportGenerator(buildOperationRunner, buildOperationExecutor, metadataRendererRegistry, reportsDirectory);
        }
    }

    private final BuildOperationRunner buildOperationRunner;
    private final BuildOperationExecutor buildOperationExecutor;
    private final MetadataRendererRegistry metadataRendererRegistry;
    private final Path reportsDirectory;

    private GenericHtmlTestReportGenerator(
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
    @Nullable
    public Path generate(List<Path> resultsDirectories) {
        List<SerializableTestResultStore> stores = resultsDirectories.stream()
            .distinct()
            .map(SerializableTestResultStore::new)
            .filter(SerializableTestResultStore::hasResults)
            .collect(Collectors.toList());

        if (stores.stream().noneMatch(SerializableTestResultStore::hasResults)) {
            return null;
        }

        try {
            Files.createDirectories(reportsDirectory);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        List<SerializableTestResultStore.OutputReader> outputReaders = new ArrayList<>(stores.size());
        try {
            for (SerializableTestResultStore store : stores) {
                outputReaders.add(store.openOutputReader());
            }

            TestTreeModel root = TestTreeModel.loadModelFromStores(stores);
            new GenericHtmlTestReport(
                buildOperationRunner, buildOperationExecutor, outputReaders, metadataRendererRegistry
            ).generateReport(root, reportsDirectory);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            CompositeStoppable.stoppable(outputReaders).stop();
        }
        return reportsDirectory.resolve("index.html");
    }

}
