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
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.testing.TestFilter;
import org.gradle.api.tasks.testing.testng.TestNGOptions;
import org.gradle.internal.Factory;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.process.internal.worker.WorkerProcessBuilder;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.List;

@UsedByScanPlugin("test-retry")
public abstract class TestNGTestFramework implements TestFramework {
    private TestNGDetector detector;
    private final DefaultTestFilter filter;
    private final Factory<File> testTaskTemporaryDir;
    private final Provider<Boolean> dryRun;
    private final DirectoryReport html;

    @Inject
    public TestNGTestFramework(DefaultTestFilter filter, Factory<File> testTaskTemporaryDir, Provider<Boolean> dryRun, DirectoryReport html) {
        this.filter = filter;
        this.testTaskTemporaryDir = testTaskTemporaryDir;
        this.detector = new TestNGDetector(new ClassFileExtractionManager(testTaskTemporaryDir));
        this.dryRun = dryRun;
        this.html = html;
        conventionMapOutputDirectory(getOptions(), html);
    }

    private static void conventionMapOutputDirectory(TestNGOptions options, final DirectoryReport html) {
        new DslObject(options).getConventionMapping().map("outputDirectory", () -> html.getOutputLocation().getAsFile().getOrNull());
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @UsedByScanPlugin("test-retry")
    @Override
    public TestFramework copyWithFilters(TestFilter newTestFilters) {
        TestNGTestFramework newTestFramework = getObjectFactory().newInstance(TestNGTestFramework.class, newTestFilters, testTaskTemporaryDir, dryRun, html);
        newTestFramework.getOptions().copyFrom(getOptions());

        return newTestFramework;
    }

    @Override
    public WorkerTestClassProcessorFactory getProcessorFactory() {
        List<File> suiteFiles = getOptions().getSuites(testTaskTemporaryDir.create());
        TestNGSpec spec = toSpec(getOptions(), filter);
        return new TestNgTestClassProcessorFactory(this.getOptions().getOutputDirectory(), spec, suiteFiles);
    }

    private TestNGSpec toSpec(TestNGOptions options, DefaultTestFilter filter) {
        return new TestNGSpec(filter.toSpec(),
            options.getSuiteName(), options.getTestName(), options.getParallel(), options.getThreadCount(), options.getSuiteThreadPoolSize().get(),
            options.getUseDefaultListeners(), options.getThreadPoolFactoryClass(),
            options.getIncludeGroups(), options.getExcludeGroups(), options.getListeners(),
            options.getConfigFailurePolicy(), options.getPreserveOrder(), options.getGroupByInstances(), dryRun.get()
        );
    }

    @Override
    public Action<WorkerProcessBuilder> getWorkerConfigurationAction() {
        return workerProcessBuilder -> workerProcessBuilder.sharedPackages("org.testng");
    }

    @Override
    @Nested
    public abstract TestNGOptions getOptions();

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
