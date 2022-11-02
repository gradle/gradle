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

package org.gradle.internal.enterprise.test.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.tasks.properties.TaskProperties;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestFrameworkOptions;
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions;
import org.gradle.internal.enterprise.test.CandidateClassFile;
import org.gradle.internal.enterprise.test.InputFileProperty;
import org.gradle.internal.enterprise.test.OutputFileProperty;
import org.gradle.internal.enterprise.test.TestTaskFilters;
import org.gradle.internal.enterprise.test.TestTaskForkOptions;
import org.gradle.internal.enterprise.test.TestTaskProperties;
import org.gradle.internal.enterprise.test.TestTaskPropertiesService;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.DefaultProcessForkOptions;
import org.gradle.process.internal.JavaForkOptionsFactory;

import javax.inject.Inject;
import java.util.Set;
import java.util.function.Function;

import static org.gradle.api.internal.tasks.properties.TaskProperties.ResolutionState.FINALIZED;

public class DefaultTestTaskPropertiesService implements TestTaskPropertiesService {

    private final JavaForkOptionsFactory forkOptionsFactory;
    private final JvmVersionDetector jvmVersionDetector;
    private final JavaModuleDetector javaModuleDetector;

    @Inject
    public DefaultTestTaskPropertiesService(
        JavaForkOptionsFactory forkOptionsFactory,
        JvmVersionDetector jvmVersionDetector,
        JavaModuleDetector javaModuleDetector
    ) {
        this.forkOptionsFactory = forkOptionsFactory;
        this.jvmVersionDetector = jvmVersionDetector;
        this.javaModuleDetector = javaModuleDetector;
    }

    @Override
    public TestTaskProperties collectProperties(Test task) {
        TaskProperties taskProperties = task.getTaskProperties(FINALIZED);

        ImmutableList<InputFileProperty> inputFileProperties = taskProperties.getInputFileProperties().stream()
            .map(inputFileProperty -> new DefaultInputFileProperty(inputFileProperty.getPropertyName(), inputFileProperty.getPropertyFiles()))
            .collect(ImmutableList.toImmutableList());

        ImmutableList<OutputFileProperty> outputFileProperties = taskProperties.getOutputFileProperties().stream()
            .map(outputFileProperty -> {
                OutputFileProperty.Type type = outputFileProperty.getOutputType() == TreeType.DIRECTORY
                    ? OutputFileProperty.Type.DIRECTORY
                    : OutputFileProperty.Type.FILE;
                return new DefaultOutputFileProperty(outputFileProperty.getPropertyName(), outputFileProperty.getPropertyFiles(), type);
            })
            .collect(ImmutableList.toImmutableList());

        return new DefaultTestTaskProperties(
            task.getOptions() instanceof JUnitPlatformOptions,
            task.getForkEvery(),
            collectFilters(task),
            collectForkOptions(task),
            collectCandidateClassFiles(task),
            inputFileProperties,
            outputFileProperties
        );
    }

    private ImmutableList<CandidateClassFile> collectCandidateClassFiles(Test task) {
        ImmutableList.Builder<CandidateClassFile> builder = ImmutableList.builder();
        task.getCandidateClassFiles().visit(new EmptyFileVisitor() {
            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                builder.add(new DefaultCandidateClassFile(fileDetails.getFile(), fileDetails.getPath()));
            }
        });
        return builder.build();
    }

    private TestTaskFilters collectFilters(Test task) {
        DefaultTestFilter filter = (DefaultTestFilter) task.getFilter();
        TestFrameworkOptions options = task.getOptions();
        return new DefaultTestTaskFilters(
            filter.getIncludePatterns(),
            filter.getCommandLineIncludePatterns(),
            filter.getExcludePatterns(),
            getOrEmpty(options, JUnitPlatformOptions::getIncludeTags),
            getOrEmpty(options, JUnitPlatformOptions::getExcludeTags),
            getOrEmpty(options, JUnitPlatformOptions::getIncludeEngines),
            getOrEmpty(options, JUnitPlatformOptions::getExcludeEngines)
        );
    }

    private <T> Set<T> getOrEmpty(TestFrameworkOptions options, Function<JUnitPlatformOptions, Set<T>> extractor) {
        return options instanceof JUnitPlatformOptions
            ? extractor.apply((JUnitPlatformOptions) options)
            : ImmutableSet.of();
    }

    private TestTaskForkOptions collectForkOptions(Test task) {
        boolean testIsModule = javaModuleDetector.isModule(task.getModularity().getInferModulePath().get(), task.getTestClassesDirs());
        JavaForkOptions forkOptions = forkOptionsFactory.newJavaForkOptions();
        task.copyTo(forkOptions);
        String executable = forkOptions.getExecutable();
        return new DefaultTestTaskForkOptions(
            forkOptions.getWorkingDir(),
            executable,
            detectJavaVersion(executable),
            javaModuleDetector.inferClasspath(testIsModule, task.getClasspath()),
            javaModuleDetector.inferModulePath(testIsModule, task.getClasspath()),
            forkOptions.getAllJvmArgs(),
            DefaultProcessForkOptions.getActualEnvironment(forkOptions)
        );
    }

    private int detectJavaVersion(String executable) {
        return Integer.parseInt(jvmVersionDetector.getJavaVersion(executable).getMajorVersion());
    }
}
