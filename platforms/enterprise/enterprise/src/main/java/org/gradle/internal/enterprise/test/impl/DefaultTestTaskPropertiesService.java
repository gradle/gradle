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
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.TaskOutputsEnterpriseInternal;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.tasks.TaskPropertyUtils;
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
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.FileNormalizer;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.internal.properties.InputBehavior;
import org.gradle.internal.properties.InputFilePropertyType;
import org.gradle.internal.properties.OutputFilePropertyType;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.properties.bean.PropertyWalker;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.DefaultProcessForkOptions;
import org.gradle.process.internal.JavaForkOptionsFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Set;
import java.util.function.Function;

public class DefaultTestTaskPropertiesService implements TestTaskPropertiesService {

    private final PropertyWalker propertyWalker;
    private final FileCollectionFactory fileCollectionFactory;
    private final JavaForkOptionsFactory forkOptionsFactory;
    private final JvmVersionDetector jvmVersionDetector;
    private final JavaModuleDetector javaModuleDetector;

    @Inject
    public DefaultTestTaskPropertiesService(
        PropertyWalker propertyWalker,
        FileCollectionFactory fileCollectionFactory,
        JavaForkOptionsFactory forkOptionsFactory,
        JvmVersionDetector jvmVersionDetector,
        JavaModuleDetector javaModuleDetector
    ) {
        this.propertyWalker = propertyWalker;
        this.fileCollectionFactory = fileCollectionFactory;
        this.forkOptionsFactory = forkOptionsFactory;
        this.jvmVersionDetector = jvmVersionDetector;
        this.javaModuleDetector = javaModuleDetector;
    }

    @Override
    public TestTaskProperties collectProperties(Test task) {
        ImmutableList.Builder<InputFileProperty> inputFileProperties = ImmutableList.builder();
        ImmutableList.Builder<OutputFileProperty> outputFileProperties = ImmutableList.builder();
        TaskPropertyUtils.visitProperties(propertyWalker, task, new PropertyVisitor() {
            @Override
            public void visitInputFileProperty(
                String propertyName,
                boolean optional,
                InputBehavior behavior,
                DirectorySensitivity directorySensitivity,
                LineEndingSensitivity lineEndingSensitivity,
                @Nullable FileNormalizer fileNormalizer,
                PropertyValue value,
                InputFilePropertyType filePropertyType
            ) {
                FileCollection files = resolveLeniently(value);
                inputFileProperties.add(new DefaultInputFileProperty(propertyName, files));
            }

            @Override
            public void visitOutputFileProperty(
                String propertyName,
                boolean optional,
                PropertyValue value,
                OutputFilePropertyType filePropertyType
            ) {
                FileCollection files = resolveLeniently(value);
                OutputFileProperty.Type type = filePropertyType.getOutputType() == TreeType.DIRECTORY
                    ? OutputFileProperty.Type.DIRECTORY
                    : OutputFileProperty.Type.FILE;
                outputFileProperties.add(new DefaultOutputFileProperty(propertyName, files, type));
            }
        });
        return new DefaultTestTaskProperties(
            task.getOptions() instanceof JUnitPlatformOptions,
            task.getForkEvery(),
            task.getDryRun().get(),
            collectFilters(task),
            collectForkOptions(task),
            collectCandidateClassFiles(task),
            inputFileProperties.build(),
            outputFileProperties.build()
        );
    }

    @Override
    public void doNotStoreInCache(Test task) {
        ((TaskOutputsEnterpriseInternal) task.getOutputs()).doNotStoreInCache();
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

    private FileCollection resolveLeniently(PropertyValue value) {
        Object sources = value.call();
        return sources == null
            ? FileCollectionFactory.empty()
            : fileCollectionFactory.resolvingLeniently(sources);
    }

    private TestTaskFilters collectFilters(Test task) {
        DefaultTestFilter filter = (DefaultTestFilter) task.getFilter();
        TestFrameworkOptions options = task.getOptions();
        return new DefaultTestTaskFilters(
            filter.getIncludePatterns(),
            filter.getCommandLineIncludePatterns(),
            filter.getExcludePatterns(),
            getOrEmpty(options, o -> o.getIncludeTags().get()),
            getOrEmpty(options, o -> o.getExcludeTags().get()),
            getOrEmpty(options, o -> o.getIncludeEngines().get()),
            getOrEmpty(options, o -> o.getExcludeEngines().get())
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
            forkOptions.getAllJvmArgs().get(),
            DefaultProcessForkOptions.getActualEnvironment(forkOptions)
        );
    }

    private int detectJavaVersion(String executable) {
        return jvmVersionDetector.getJavaVersionMajor(executable);
    }
}
