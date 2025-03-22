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
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
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
