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

import org.gradle.api.NonNullApi;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.testing.report.generic.GenericHtmlTestReport;
import org.gradle.api.internal.tasks.testing.report.generic.TestTreeModel;
import org.gradle.api.internal.tasks.testing.results.SerializableTestResultStore;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRunner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@NonNullApi
public class GenericTestReportImplementation implements TestReportImplementation {
    private final List<SerializableTestResultStore> stores;

    public GenericTestReportImplementation(FileCollection resultDirs) {
        Set<File> dirs = resultDirs.getFiles();
        List<SerializableTestResultStore> stores = new ArrayList<>(dirs.size());
        for (File dir : dirs) {
            SerializableTestResultStore store = new SerializableTestResultStore(dir.toPath());
            if (!store.hasResults()) {
                continue;
            }
            stores.add(store);
        }
        this.stores = stores;
    }

    @Override
    public boolean hasResults() {
        // We don't need to check the stores, as we only keep them if they have results
        return !stores.isEmpty();
    }

    @Override
    public void generateReport(BuildOperationRunner operationRunner, BuildOperationExecutor operationExecutor, File asFile) {
        Map<String, SerializableTestResultStore.OutputReader> outputReaders = new HashMap<>(stores.size());
        try {
            for (SerializableTestResultStore store : stores) {
                outputReaders.put(store.getRootResult().getTestResult().getName(), store.openOutputReader());
            }

            TestTreeModel root = TestTreeModel.loadModelFromStores(stores);
            new GenericHtmlTestReport(operationRunner, operationExecutor, outputReaders).generateReport(root, asFile.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            CompositeStoppable.stoppable(outputReaders.values()).stop();
        }
    }

    @Override
    public void close() {
        // Nothing to close, we only open things during the report generation
    }
}
