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
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.testing.GenericTestReportGenerator;
import org.gradle.api.internal.tasks.testing.LegacyTestReportGenerator;
import org.gradle.api.internal.tasks.testing.TestReportGenerator;
import org.gradle.api.internal.tasks.testing.report.generic.MetadataRendererRegistry;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
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
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor;
import org.gradle.internal.instrumentation.api.annotations.ReplacedDeprecation;
import org.gradle.internal.instrumentation.api.annotations.ReplacedDeprecation.RemovedIn;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.util.stream.Collectors;

import static org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType.GETTER;
import static org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType.SETTER;
import static org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty.BinaryCompatibility.ACCESSORS_KEPT;

/**
 * Generates an HTML test report from the results of one or more {@link Test} tasks.
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
public abstract class TestReport extends DefaultTask {
    private final DirectoryProperty destinationDir = getObjectFactory().directoryProperty();
    private final ConfigurableFileCollection resultDirs = getObjectFactory().fileCollection();

    @Inject
    protected abstract BuildOperationRunner getBuildOperationRunner();

    @Inject
    protected abstract BuildOperationExecutor getBuildOperationExecutor();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Inject
    protected abstract MetadataRendererRegistry getMetadataRendererRegistry();

    /**
     * Returns the directory to write the HTML report to.
     *
     * <strong>This method is {@code @Deprecated}, please use {@link #getDestinationDirectory()} instead.</strong>
     */
    @ReplacedBy("destinationDirectory")
    @Deprecated
    public File getDestinationDir() {
        DeprecationLogger.deprecateProperty(TestReport.class, "destinationDir").replaceWith("destinationDirectory")
                .willBeRemovedInGradle9()
                .withDslReference()
                .nagUser();
        return getDestinationDirectory().get().getAsFile();
    }

    /**
     * Sets the directory to write the HTML report to.
     *
     * <strong>This method is {@code @Deprecated}, please use {@link #getTestResults()} instead to access the new collection property.</strong>
     */
    @Deprecated
    public void setDestinationDir(File destinationDir) {
        DeprecationLogger.deprecateProperty(TestReport.class, "destinationDir").replaceWith("destinationDirectory")
                .willBeRemovedInGradle9()
                .withDslReference()
                .nagUser();
        getDestinationDirectory().set(destinationDir);
    }

    /**
     * Returns the directory to write the HTML report to.
     *
     * @since 7.4
     */
    @OutputDirectory
    @ReplacesEagerProperty(
        replacedAccessors = {
            @ReplacedAccessor(value = GETTER, name = "getDestinationDir"),
            @ReplacedAccessor(value = SETTER, name = "setDestinationDir")
        },
        binaryCompatibility = ACCESSORS_KEPT,
        deprecation = @ReplacedDeprecation(removedIn = RemovedIn.GRADLE9, withDslReference = true)
    )
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
     * <strong>This method is {@code @Deprecated} - use {@link #getTestResults()} and invoke {@link ConfigurableFileCollection#from(Object...)} instead,
     * passing references to {@link AbstractTestTask#getBinaryResultsDirectory()} as arguments.</strong>
     *
     * @param results The result objects.
     */
    @Deprecated
    public void reportOn(Object... results) {
        DeprecationLogger.deprecateMethod(TestReport.class, "reportOn(Object...)").replaceWith("testResults")
                .withAdvice("invoke getTestResults().from(Object...) instead, passing references to Test#getBinaryResultsDirectory() as arguments.")
                .willBeRemovedInGradle9()
                .withDslReference(TestReport.class, "testResults")
                .nagUser();
        for (Object result : results) {
            addTo(result, getTestResults());
        }
    }

    @TaskAction
    void generateReport() {
        try {
            TestReportGenerator impl = detectAndCreateImplementation(getTestResults());
            if (impl.hasResults()) {
                impl.generateReport(getBuildOperationRunner(), getBuildOperationExecutor(), getDestinationDirectory().get().getAsFile().toPath());
            } else {
                getLogger().info("{} - no binary test results found in dirs: {}.", getPath(), getTestResults().getFiles());
                setDidWork(false);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not write test report for results in " + getTestResults().getFiles(), e);
        }
    }

    TestReportGenerator detectAndCreateImplementation(FileCollection resultDirs) {
        Boolean isGenericImplementation = null;
        for (File resultDir : resultDirs.getFiles()) {
            boolean resultDirIsGenericImplementation = SerializableTestResultStore.isGenericTestResults(resultDir);
            if (isGenericImplementation == null) {
                isGenericImplementation = resultDirIsGenericImplementation;
            } else if (isGenericImplementation != resultDirIsGenericImplementation) {
                throw new IllegalStateException("Cannot mix generic and non-generic test results in the same report.");
            }
        }
        assert isGenericImplementation != null : "@SkipWhenEmpty should prevent this from being called with an empty collection";
        if (isGenericImplementation) {
            return new GenericTestReportGenerator(resultDirs.getFiles().stream().map(File::toPath).collect(Collectors.toSet()), getMetadataRendererRegistry());
        } else {
            return new LegacyTestReportGenerator(resultDirs);
        }
    }
}
