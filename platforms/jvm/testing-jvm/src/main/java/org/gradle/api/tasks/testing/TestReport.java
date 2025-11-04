/*
 * Copyright 2025 the original author or authors.
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
import org.gradle.api.internal.tasks.testing.LegacyHtmlTestReportGenerator;
import org.gradle.api.internal.tasks.testing.report.generic.GenericHtmlTestReportGenerator;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType.GETTER;
import static org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType.SETTER;

/**
 * Generates an HTML test report from the results of one or more {@link Test} tasks.
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
public abstract class TestReport extends DefaultTask {

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    // Method kept for binary compatibility.
    @SuppressWarnings("unused")
    @Inject
    protected abstract BuildOperationRunner getBuildOperationRunner();

    // Method kept for binary compatibility.
    @SuppressWarnings("unused")
    @Inject
    protected abstract BuildOperationExecutor getBuildOperationExecutor();

    // Method kept for binary compatibility.
    @SuppressWarnings("unused")
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
        deprecation = @ReplacedDeprecation(removedIn = RemovedIn.GRADLE9)
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
            List<Path> resultDirsAsPaths = new ArrayList<>(getTestResults().getFiles().size());
            boolean isGenericImplementation = isGenericImplementation(resultDirsAsPaths);

            Path reportsDir = getDestinationDirectory().get().getAsFile().toPath();
            getObjectFactory().newInstance(
                isGenericImplementation ? GenericHtmlTestReportGenerator.class : LegacyHtmlTestReportGenerator.class,
                reportsDir
            ).generate(resultDirsAsPaths);
        } catch (Exception e) {
            throw new RuntimeException("Could not write test report for results in " + getTestResults().getFiles(), e);
        }
    }

    private boolean isGenericImplementation(List<Path> resultDirsAsPaths) {
        Boolean isGenericImplementation = null;
        for (File resultDir : getTestResults().getFiles()) {
            boolean resultDirIsGenericImplementation = SerializableTestResultStore.isGenericTestResults(resultDir);
            if (isGenericImplementation == null) {
                isGenericImplementation = resultDirIsGenericImplementation;
            } else if (isGenericImplementation != resultDirIsGenericImplementation) {
                throw new IllegalStateException("Cannot mix generic and non-generic test results in the same report.");
            }
            resultDirsAsPaths.add(resultDir.toPath());
        }
        return Objects.requireNonNull(isGenericImplementation, "@SkipWhenEmpty should prevent this from being null");
    }

}
