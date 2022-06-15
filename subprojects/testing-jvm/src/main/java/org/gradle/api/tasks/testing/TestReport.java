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
import org.gradle.api.Incubating;
import org.gradle.api.Transformer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.testing.junit.result.AggregateTestResultsProvider;
import org.gradle.api.internal.tasks.testing.junit.result.BinaryResultBackedTestResultsProvider;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.gradle.api.internal.tasks.testing.report.DefaultTestReport;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.deprecation.DeprecationLogger;
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
     * <strong>This method will be {@code @Deprecated} soon, please use {@link #getDestinationDirectory()} instead.</strong>
     */
    @ReplacedBy("destinationDirectory")
    public File getDestinationDir() {
        DeprecationLogger.deprecateProperty(TestReport.class, "destinationDir").replaceWith("destinationDirectory")
            .willBeRemovedInGradle8()
            .withDslReference()
            .nagUser();
        return getDestinationDirectory().get().getAsFile();
    }

    /**
     * Sets the directory to write the HTML report to.
     *
     * <strong>This method will be {@code @Deprecated} soon, please use {@link #getTestResults()} instead to access the new collection property.</strong>
     */
    public void setDestinationDir(File destinationDir) {
        DeprecationLogger.deprecateProperty(TestReport.class, "destinationDir").replaceWith("destinationDirectory")
            .willBeRemovedInGradle8()
            .withDslReference()
            .nagUser();
        getDestinationDirectory().set(destinationDir);
    }

    /**
     * Returns the directory to write the HTML report to.
     *
     * @since 7.4
     */
    @Incubating
    @OutputDirectory
    public DirectoryProperty getDestinationDirectory() {
        return this.destinationDir;
    }

    /**
     * Returns the set of binary test results to include in the report.
     *
     * <strong>This method will be {@code @Deprecated} soon, please use {@link #getTestResults()} ()} instead.</strong>
     */
    @ReplacedBy("testResults")
    public FileCollection getTestResultDirs() {
        DeprecationLogger.deprecateProperty(TestReport.class, "testResultDirs").replaceWith("testResults")
            .willBeRemovedInGradle8()
            .withDslReference()
            .nagUser();
        return resultDirs;
    }

    /**
     * Returns the set of binary test results to include in the report.
     *
     * @since 7.4
     */
    @Incubating
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

    /**
     * Sets the binary test results to use to include in the report. Each entry must point to a binary test results directory generated by a {@link Test}
     * task.
     *
     * <strong>This method will be {@code @Deprecated} soon, please use {@link #getTestResults()} instead to access the new collection property.</strong>
     */
    public void setTestResultDirs(Iterable<File> testResultDirs) {
        DeprecationLogger.deprecateProperty(TestReport.class, "testResultDirs").replaceWith("testResults")
            .willBeRemovedInGradle8()
            .withDslReference()
            .nagUser();
        resultDirs.setFrom(testResultDirs);
    }

    /**
     * Adds some results to include in the report.
     *
     * <p>This method accepts any parameter of the given types:
     *
     * <ul>
     *
     * <li>A {@link Test} task instance. The results from the test task are included in the report. The test task is automatically added
     * as a dependency of this task.</li>
     *
     * <li>Anything that can be converted to a set of {@link File} instances as per {@link org.gradle.api.Project#files(Object...)}. These must
     * point to the binary test results directory generated by a {@link Test} task instance.</li>
     *
     * <li>An {@link Iterable}. The contents of the iterable are converted recursively.</li>
     *
     * </ul>
     *
     * <strong>This method will be {@code Deprecated} soon - use {@link #getTestResults()} and invoke {@link ConfigurableFileCollection#from(Object...)} instead,
     * passing references to {@link AbstractTestTask#getBinaryResultsDirectory()} as arguments.</strong>
     *
     * @param results The result objects.
     */
    public void reportOn(Object... results) {
        DeprecationLogger.deprecateMethod(TestReport.class, "reportOn(Object...)").replaceWith("testResults")
            .withAdvice("invoke getTestResults().from(Object...) instead, passing references to Test#getBinaryResultsDirectory() as arguments.")
            .willBeRemovedInGradle8()
            .withDslReference(TestReport.class, "testResults")
            .nagUser();
        for (Object result : results) {
            addTo(result, getTestResults());
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
