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

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.TestFrameworkDistributionModule;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.detection.ClassFileExtractionManager;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestFilter;
import org.gradle.api.tasks.testing.junit.JUnitOptions;
import org.gradle.internal.Factory;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.process.internal.worker.WorkerProcessBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@UsedByScanPlugin("test-retry")
public class JUnitTestFramework implements TestFramework {

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

    public JUnitTestFramework(Test testTask, DefaultTestFilter filter, boolean useImplementationDependencies) {
        this(filter, useImplementationDependencies, new JUnitOptions(), testTask.getTemporaryDirFactory());
    }

    private JUnitTestFramework(DefaultTestFilter filter, boolean useImplementationDependencies, JUnitOptions options, Factory<File> testTaskTemporaryDir) {
        this.filter = filter;
        this.useImplementationDependencies = useImplementationDependencies;
        this.options = options;
        this.testTaskTemporaryDir = testTaskTemporaryDir;
        this.detector = new JUnitDetector(new ClassFileExtractionManager(testTaskTemporaryDir));
    }

    @UsedByScanPlugin("test-retry")
    @Override
    public TestFramework copyWithFilters(TestFilter newTestFilters) {
        JUnitOptions copiedOptions = new JUnitOptions();
        copiedOptions.copyFrom(options);

        return new JUnitTestFramework(
            (DefaultTestFilter) newTestFilters,
            useImplementationDependencies,
            copiedOptions,
            testTaskTemporaryDir
        );
    }

    @Override
    public WorkerTestClassProcessorFactory getProcessorFactory() {
        return new JUnitTestClassProcessorFactory(new JUnitSpec(
            filter.toSpec(), options.getIncludeCategories(), options.getExcludeCategories()));
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

}
