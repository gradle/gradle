/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit;

import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.WorkerTestDefinitionProcessorFactory;
import org.gradle.api.internal.tasks.testing.detection.ClassFileExtractionManager;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.testing.TestFilter;
import org.gradle.api.tasks.testing.junit.JUnitOptions;
import org.gradle.internal.Factory;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.process.internal.worker.WorkerProcessBuilder;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@UsedByScanPlugin("test-retry")
public abstract class JUnitTestFramework implements TestFramework {
    private static final Logger LOGGER = Logging.getLogger(JUnitTestFramework.class);

    private JUnitDetector detector;
    private final DefaultTestFilter filter;
    private final Factory<File> testTaskTemporaryDir;
    private final Provider<Boolean> dryRun;

    @Inject
    public JUnitTestFramework(DefaultTestFilter filter, Factory<File> testTaskTemporaryDir, Provider<Boolean> dryRun) {
        this.filter = filter;
        this.testTaskTemporaryDir = testTaskTemporaryDir;
        this.detector = new JUnitDetector(new ClassFileExtractionManager(testTaskTemporaryDir));
        this.dryRun = dryRun;
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @UsedByScanPlugin("test-retry")
    @Override
    public TestFramework copyWithFilters(TestFilter newTestFilters) {
        JUnitTestFramework newTestFramework = getObjectFactory().newInstance(
            JUnitTestFramework.class,
            newTestFilters,
            testTaskTemporaryDir,
            dryRun
        );
        newTestFramework.getOptions().copyFrom(getOptions());
        return newTestFramework;
    }

    @Override
    public WorkerTestDefinitionProcessorFactory<?> getProcessorFactory() {
        validateOptions();
        return new JUnitTestDefinitionProcessorFactory(new JUnitSpec(
            filter.toSpec(),
            // JUnitSpec get serialized to worker, so we create a copy of original set,
            // to avoid serialization issues on worker for ImmutableSet that SetProperty returns
            new LinkedHashSet<>(getOptions().getIncludeCategories().get()),
            new LinkedHashSet<>(getOptions().getExcludeCategories().get()),
            dryRun.get()
        ));
    }

    @Override
    public Action<WorkerProcessBuilder> getWorkerConfigurationAction() {
        return workerProcessBuilder -> {
            workerProcessBuilder.sharedPackages("junit.framework");
            workerProcessBuilder.sharedPackages("junit.extensions");
            workerProcessBuilder.sharedPackages("org.junit");
        };
    }

    @Override
    @Nested
    public abstract JUnitOptions getOptions();

    @Override
    public JUnitDetector getDetector() {
        return detector;
    }

    @Override
    public void close() throws IOException {
        // Clear expensive state from the test framework to avoid holding on to memory
        // This should probably be a part of the test task and managed there.
        detector = null;
    }

    private void validateOptions() {
        Set<String> intersection = Sets.newHashSet(getOptions().getIncludeCategories().get());
        intersection.retainAll(getOptions().getExcludeCategories().get());
        if (!intersection.isEmpty()) {
            if (intersection.size() == 1) {
                LOGGER.warn("The category '" + intersection.iterator().next() + "' is both included and excluded.  " +
                    "This will result in the category being excluded, which may not be what was intended.  " +
                    "Please either include or exclude the category but not both.");
            } else {
                String allCategories = intersection.stream().sorted().map(s -> "'" + s + "'").collect(Collectors.joining(", "));
                LOGGER.warn("The categories " + allCategories + " are both included and excluded.  " +
                    "This will result in the categories being excluded, which may not be what was intended. " +
                    "Please either include or exclude the categories but not both.");
            }
        }
    }

    @Override
    public String getDisplayName() {
        return "JUnit";
    }
}
