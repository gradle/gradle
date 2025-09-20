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
import org.gradle.api.internal.tasks.testing.junit.result.Binary2JUnitXmlReportGenerator;
import org.gradle.api.internal.tasks.testing.junit.result.JUnitXmlResultOptions;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.UncheckedException;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Generates a JUnit XML report based on test results based on binary results from {@link SerializableTestResultStore}.
 *
 * <p>
 * This assumes a specific structure of tests from the {@code Test} task, and will not include the intermediate nodes as not all
 * JUnit XML consumers allow them.
 * </p>
 *
 * <p>
 * The root results are recorded into `index.html`, and then each parent tells its children to generate starting at `{childName}/index.html`.
 */
public abstract class JunitXmlTestReportGenerator implements TestReportGenerator {
    private final ObjectFactory objectFactory;
    private final Path reportsDirectory;
    private final JUnitXmlResultOptions xmlResultOptions;

    @Inject
    public JunitXmlTestReportGenerator(ObjectFactory objectFactory, Path reportsDirectory, JUnitXmlResultOptions xmlResultOptions) {
        this.objectFactory = objectFactory;
        this.reportsDirectory = reportsDirectory;
        this.xmlResultOptions = xmlResultOptions;
    }

    @Override
    @Nullable
    public Path generate(List<Path> resultsDirectories) {
        if (resultsDirectories.isEmpty()) {
            return null;
        }
        if (resultsDirectories.size() > 1) {
            throw new IllegalArgumentException("JunitXmlTestReportGenerator can only generate a report from a single results directory. Found: " + resultsDirectories);
        }

        SerializableTestResultStore resultsStore = new SerializableTestResultStore(resultsDirectories.get(0));
        if (!resultsStore.hasResults()) {
            return null;
        }

        try {
            Files.createDirectories(reportsDirectory);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }


        try (SerializableTestResultStore.OutputReader outputReader = resultsStore.openOutputReader()) {
            TestTreeModel root = TestTreeModel.loadModelFromStores(Collections.singletonList(resultsStore));
            objectFactory.newInstance(
                Binary2JUnitXmlReportGenerator.class,
                reportsDirectory.toFile(), new TestTreeModelResultsProvider(root, outputReader), xmlResultOptions
            ).generate();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        return reportsDirectory;
    }
}
