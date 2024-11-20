/*
 * Copyright 2021 the original author or authors.
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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.internal.tasks.testing.junit.result.AggregateTestResultsProvider;
import org.gradle.api.internal.tasks.testing.junit.result.BinaryResultBackedTestResultsProvider;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.gradle.api.internal.tasks.testing.report.HtmlTestReport;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.AggregateTestReport;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.util.LinkedList;
import java.util.List;

import static org.gradle.internal.concurrent.CompositeStoppable.stoppable;
import static org.gradle.util.internal.CollectionUtils.collect;

public abstract class DefaultAggregateTestReport implements AggregateTestReport {

    private final String name;
    private final TaskDependencyInternal buildDependencies;

    @Inject
    public DefaultAggregateTestReport(
        String name,
        TaskDependencyFactory taskDependencyFactory,
        TaskContainer tasks
    ) {
        this.name = name;
        TaskProvider<AggregateTestReportTask> reportTask = tasks.register(name, AggregateTestReportTask.class, task -> {
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.setDescription("Generates aggregated test report.");

            task.getTestResults().from(getBinaryTestResults());
            task.getDestinationDirectory().set(getHtmlReportDirectory());
        });

        this.buildDependencies = taskDependencyFactory.configurableDependency(ImmutableSet.of(reportTask));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return buildDependencies;
    }

    // TODO: This class was copied from org.gradle.testing.base.internal.TestReport, except with deprecated
    // code removed. This allows us to move DefaultAggregateTestReport to a non-jvm-specific project.
    // In 9.0, we should migrate this task to the existing public TestReport task.

    @DisableCachingByDefault(because = "Not made cacheable, yet")
    public static abstract class AggregateTestReportTask extends DefaultTask {

        private final BuildOperationRunner buildOperationRunner;
        private final BuildOperationExecutor buildOperationExecutor;

        @Inject
        public AggregateTestReportTask(
            BuildOperationRunner buildOperationRunner,
            BuildOperationExecutor buildOperationExecutor
        ) {
            this.buildOperationRunner = buildOperationRunner;
            this.buildOperationExecutor = buildOperationExecutor;
        }

        /**
         * Returns the directory to write the HTML report to.
         *
         * @since 7.4
         */
        @OutputDirectory
        public abstract DirectoryProperty getDestinationDirectory();

        /**
         * Returns the set of binary test results to include in the report.
         *
         * @since 7.4
         */
        @InputFiles
        @SkipWhenEmpty
        @IgnoreEmptyDirectories
        @PathSensitive(PathSensitivity.NONE)
        public abstract ConfigurableFileCollection getTestResults();

        @TaskAction
        void generateReport() {
            TestResultsProvider resultsProvider = createAggregateProvider();
            try {
                if (resultsProvider.isHasResults()) {
                    HtmlTestReport testReport = new HtmlTestReport(buildOperationRunner, buildOperationExecutor);
                    testReport.generateReport(resultsProvider, getDestinationDirectory().get().getAsFile());
                } else {
                    getLogger().info("{} - no binary test results found in dirs: {}.", getPath(), getTestResults().getFiles());
                    setDidWork(false);
                }
            } finally {
                stoppable(resultsProvider).stop();
            }
        }

        private TestResultsProvider createAggregateProvider() {
            List<TestResultsProvider> resultsProviders = new LinkedList<>();
            try {
                FileCollection resultDirs = getTestResults();
                if (resultDirs.getFiles().size() == 1) {
                    return new BinaryResultBackedTestResultsProvider(resultDirs.getSingleFile());
                } else {
                    return new AggregateTestResultsProvider(collect(resultDirs, resultsProviders, BinaryResultBackedTestResultsProvider::new));
                }
            } catch (RuntimeException e) {
                stoppable(resultsProviders).stop();
                throw e;
            }
        }
    }

}
