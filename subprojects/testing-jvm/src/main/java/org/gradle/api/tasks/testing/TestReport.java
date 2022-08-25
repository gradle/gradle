/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.testing;

import org.gradle.api.DefaultTask;
import org.gradle.api.Transformer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.testing.junit.result.AggregateTestResultsProvider;
import org.gradle.api.internal.tasks.testing.junit.result.BinaryResultBackedTestResultsProvider;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.gradle.api.internal.tasks.testing.report.DefaultTestReport;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.util.LinkedList;
import java.util.List;

import static org.gradle.internal.concurrent.CompositeStoppable.stoppable;
import static org.gradle.util.internal.CollectionUtils.collect;

/**
 * Generates an HTML test report from the results of one or more {@link Test} tasks.
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
public class TestReport extends DefaultTask {
    private final DirectoryProperty destinationDir = getObjectFactory().directoryProperty();
    private final ConfigurableFileCollection resultDirs = getObjectFactory().fileCollection();

    @Inject
    protected BuildOperationExecutor getBuildOperationExecutor() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the directory to write the HTML report to.
     *
     * @since 7.4
     */
    @OutputDirectory
    public DirectoryProperty getDestinationDirectory() {
        return this.destinationDir;
    }

    /**
     * Returns the set of binary test results to include in the report.
     *
     * @since 7.4
     */
    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.NONE)
    public ConfigurableFileCollection getTestResults() {
        return resultDirs;
    }

    private void addTo(Object result, ConfigurableFileCollection dirs) {
        if (result instanceof Test) {
            Test test = (Test) result;
            dirs.from(test.getBinaryResultsDirectory());
        } else if (result instanceof Iterable<?>) {
            Iterable<?> iterable = (Iterable<?>) result;
            for (Object nested : iterable) {
                addTo(nested, dirs);
            }
        } else {
            dirs.from(result);
        }
    }

    @TaskAction
    void generateReport() {
        TestResultsProvider resultsProvider = createAggregateProvider();
        try {
            if (resultsProvider.isHasResults()) {
                DefaultTestReport testReport = new DefaultTestReport(getBuildOperationExecutor());
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
        List<TestResultsProvider> resultsProviders = new LinkedList<TestResultsProvider>();
        try {
            FileCollection resultDirs = getTestResults();
            if (resultDirs.getFiles().size() == 1) {
                return new BinaryResultBackedTestResultsProvider(resultDirs.getSingleFile());
            } else {
                return new AggregateTestResultsProvider(collect(resultDirs, resultsProviders, new Transformer<TestResultsProvider, File>() {
                    @Override
                    public TestResultsProvider transform(File dir) {
                        return new BinaryResultBackedTestResultsProvider(dir);
                    }
                }));
            }
        } catch (RuntimeException e) {
            stoppable(resultsProviders).stop();
            throw e;
        }
    }
}
