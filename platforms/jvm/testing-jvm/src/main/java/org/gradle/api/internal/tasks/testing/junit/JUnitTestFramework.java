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
import org.gradle.api.internal.tasks.testing.TestFrameworkDistributionModule;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.detection.ClassFileExtractionManager;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestFilter;
import org.gradle.api.tasks.testing.junit.JUnitOptions;
import org.gradle.internal.Factory;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.process.internal.worker.WorkerProcessBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@UsedByScanPlugin("test-retry")
public class JUnitTestFramework implements TestFramework {
    private static final Logger LOGGER = Logging.getLogger(JUnitTestFramework.class);

    private static final List<TestFrameworkDistributionModule> DISTRIBUTION_MODULES =
        Collections.singletonList(new TestFrameworkDistributionModule(
            "junit",
            Pattern.compile("junit-4.*\\.jar"),
            "org.junit.runner.Runner"
        ));

    private JUnitOptions options;
    private JUnitDetector detector;
    private final DefaultTestFilter filter;
    private final boolean useImplementationDependencies;
    private final Factory<File> testTaskTemporaryDir;
    private final Provider<Boolean> dryRun;
    private final ObjectFactory objects;

    public JUnitTestFramework(Test testTask, DefaultTestFilter filter, boolean useImplementationDependencies) {
        this(filter, useImplementationDependencies, testTask.getProject().getObjects().newInstance(JUnitOptions.class), testTask.getTemporaryDirFactory(), testTask.getDryRun(), testTask.getProject().getObjects());
    }

    private JUnitTestFramework(DefaultTestFilter filter, boolean useImplementationDependencies, JUnitOptions options, Factory<File> testTaskTemporaryDir, Provider<Boolean> dryRun, ObjectFactory objects) {
        this.filter = filter;
        this.useImplementationDependencies = useImplementationDependencies;
        this.objects = objects;
        this.options = options;
        this.testTaskTemporaryDir = testTaskTemporaryDir;
        this.detector = new JUnitDetector(new ClassFileExtractionManager(testTaskTemporaryDir));
        this.dryRun = dryRun;
    }

    @UsedByScanPlugin("test-retry")
    @Override
    public TestFramework copyWithFilters(TestFilter newTestFilters) {
        JUnitOptions copiedOptions = objects.newInstance(JUnitOptions.class);
        copiedOptions.copyFrom(options);

        return new JUnitTestFramework(
            (DefaultTestFilter) newTestFilters,
            useImplementationDependencies,
            copiedOptions,
            testTaskTemporaryDir,
            dryRun,
            objects
        );
    }

    @Override
    public WorkerTestClassProcessorFactory getProcessorFactory() {
        validateOptions();
        return new JUnitTestClassProcessorFactory(new JUnitSpec(
            filter.toSpec(),
            // JUnitSpec get serialized to worker, so we create a copy of original set,
            // to avoid serialization issues on worker for ImmutableSet that SetProperty returns
            new LinkedHashSet<>(options.getIncludeCategories().get()),
            new LinkedHashSet<>(options.getExcludeCategories().get()),
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
    public List<TestFrameworkDistributionModule> getWorkerImplementationClasspathModules() {
        return DISTRIBUTION_MODULES;
    }

    @Override
    public boolean getUseDistributionDependencies() {
        return useImplementationDependencies;
    }

    @Override
    public JUnitOptions getOptions() {
        return options;
    }

    void setOptions(JUnitOptions options) {
        this.options = options;
    }

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
        Set<String> intersection = Sets.newHashSet(options.getIncludeCategories().get());
        intersection.retainAll(options.getExcludeCategories().get());
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
}
