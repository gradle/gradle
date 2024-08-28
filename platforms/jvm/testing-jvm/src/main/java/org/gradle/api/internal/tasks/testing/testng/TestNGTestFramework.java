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

package org.gradle.api.internal.tasks.testing.testng;

import org.gradle.api.Action;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.detection.ClassFileExtractionManager;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.DirectoryReport;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestFilter;
import org.gradle.api.tasks.testing.testng.TestNGOptions;
import org.gradle.internal.Factory;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.process.internal.worker.WorkerProcessBuilder;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Callable;

@UsedByScanPlugin("test-retry")
public class TestNGTestFramework implements TestFramework {
    private final TestNGOptions options;
    private TestNGDetector detector;
    private final DefaultTestFilter filter;
    private final ObjectFactory objects;
    private final Factory<File> testTaskTemporaryDir;
    private final DirectoryReport htmlReport;
    private final Provider<Boolean> dryRun;

    public TestNGTestFramework(final Test testTask, DefaultTestFilter filter, ObjectFactory objects) {
        this(
            filter,
            objects,
            testTask.getTemporaryDirFactory(),
            testTask.getReports().getHtml(),
            objects.newInstance(TestNGOptions.class),
            testTask.getDryRun()
        );
    }

    private TestNGTestFramework(DefaultTestFilter filter, ObjectFactory objects, Factory<File> testTaskTemporaryDir, DirectoryReport htmlReport, TestNGOptions options, Provider<Boolean> dryRun) {
        this.filter = filter;
        this.objects = objects;
        this.testTaskTemporaryDir = testTaskTemporaryDir;
        this.htmlReport = htmlReport;
        this.options = options;
        this.detector = new TestNGDetector(new ClassFileExtractionManager(testTaskTemporaryDir));
        this.dryRun = dryRun;

        conventionMapOutputDirectory(options, htmlReport);
    }

    private static void conventionMapOutputDirectory(TestNGOptions options, final DirectoryReport html) {
        new DslObject(options).getConventionMapping().map("outputDirectory", new Callable<File>() {
            @Override
            public File call() {
                return html.getOutputLocation().getAsFile().getOrNull();
            }
        });
    }

    @UsedByScanPlugin("test-retry")
    @Override
    public TestFramework copyWithFilters(TestFilter newTestFilters) {
        TestNGOptions copiedOptions = objects.newInstance(TestNGOptions.class);
        copiedOptions.copyFrom(options);

        return new TestNGTestFramework(
            (DefaultTestFilter) newTestFilters,
            objects,
            testTaskTemporaryDir,
            htmlReport,
            copiedOptions,
            dryRun);
    }

    @Override
    public WorkerTestClassProcessorFactory getProcessorFactory() {
        List<File> suiteFiles = options.getSuites(testTaskTemporaryDir.create());
        TestNGSpec spec = toSpec(options, filter);
        return new TestNgTestClassProcessorFactory(this.options.getOutputDirectory().getAsFile().get(), spec, suiteFiles);
    }

    private TestNGSpec toSpec(TestNGOptions options, DefaultTestFilter filter) {
        return new TestNGSpec(filter.toSpec(),
            options.getSuiteName().get(),
            options.getTestName().get(),
            options.getParallel().getOrNull(),
            options.getThreadCount().get(),
            options.getSuiteThreadPoolSize().get(),
            options.getUseDefaultListeners().get(),
            options.getThreadPoolFactoryClass().getOrNull(),
            // TestNGSpec get serialized to worker, so we create a copy of original sets,
            // to avoid serialization issues on worker for ImmutableSet that SetProperty returns
            new LinkedHashSet<>(options.getIncludeGroups().get()),
            new LinkedHashSet<>(options.getExcludeGroups().get()),
            new LinkedHashSet<>(options.getListeners().get()),
            options.getConfigFailurePolicy().get(),
            options.getPreserveOrder().get(),
            options.getGroupByInstances().get(),
            dryRun.get()
        );
    }

    @Override
    public Action<WorkerProcessBuilder> getWorkerConfigurationAction() {
        return workerProcessBuilder -> workerProcessBuilder.sharedPackages("org.testng");
    }

    @Override
    public boolean getUseDistributionDependencies() {
        // We have no (default) implementation dependencies (see above).
        // The user must add their TestNG dependency to the test's runtimeClasspath themselves
        // or preferably use test suites where the dependencies are automatically managed.
        return false;
    }

    @Override
    public TestNGOptions getOptions() {
        return options;
    }

    @Override
    public TestNGDetector getDetector() {
        return detector;
    }

    @Override
    public void close() throws IOException {
        // Clear expensive state from the test framework to avoid holding on to memory
        // This should probably be a part of the test task and managed there.
        detector = null;
    }

}
