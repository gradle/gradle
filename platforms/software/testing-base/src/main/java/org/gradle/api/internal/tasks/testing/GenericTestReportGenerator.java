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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.tasks.testing.report.generic.GenericHtmlTestReport;
import org.gradle.api.internal.tasks.testing.report.generic.MetadataRendererRegistry;
import org.gradle.api.internal.tasks.testing.report.generic.TestTreeModel;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@NullMarked
public class GenericTestReportGenerator implements TestReportGenerator {
    private final List<SerializableTestResultStore> stores;
    private final MetadataRendererRegistry metadataRendererRegistry;

    public GenericTestReportGenerator(Collection<Path> resultDirs, MetadataRendererRegistry metadataRendererRegistry) {
        this.stores = resultDirs.stream()
            .distinct()
            .map(SerializableTestResultStore::new)
            .filter(SerializableTestResultStore::hasResults)
            .collect(Collectors.toList());
        this.metadataRendererRegistry = metadataRendererRegistry;
    }

    @Override
    public boolean hasResults() {
        // We don't need to check the stores, as we only keep them if they have results
        return !stores.isEmpty();
    }

    @Override
    public void generateReport(BuildOperationRunner operationRunner, BuildOperationExecutor operationExecutor, Path outputDir) {
        if (!hasResults()) {
            return;
        }
        List<SerializableTestResultStore.OutputReader> outputReaders = new ArrayList<>(stores.size());
        try {
            for (SerializableTestResultStore store : stores) {
                outputReaders.add(store.openOutputReader());
            }

            TestTreeModel root = TestTreeModel.loadModelFromStores(stores);
            new GenericHtmlTestReport(operationRunner, operationExecutor, outputReaders, metadataRendererRegistry).generateReport(root, outputDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            CompositeStoppable.stoppable(outputReaders).stop();
        }
    }
}
