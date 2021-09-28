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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.tasks.TaskPropertyUtils;
import org.gradle.api.internal.tasks.properties.ContentTracking;
import org.gradle.api.internal.tasks.properties.InputFilePropertyType;
import org.gradle.api.internal.tasks.properties.OutputFilePropertyType;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestFrameworkOptions;
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions;
import org.gradle.internal.enterprise.test.TestTaskForkOptions;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.enterprise.test.InputFileProperty;
import org.gradle.internal.enterprise.test.OutputFileProperty;
import org.gradle.internal.enterprise.test.TaskPropertiesService;
import org.gradle.internal.enterprise.test.TestTaskFilters;
import org.gradle.internal.enterprise.test.TestTaskProperties;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.process.internal.DefaultJavaForkOptions;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Set;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.gradle.internal.Cast.uncheckedCast;

public class DefaultTaskPropertiesService implements TaskPropertiesService {

    private final PropertyWalker propertyWalker;
    private final FileCollectionFactory fileCollectionFactory;
    private final JvmVersionDetector jvmVersionDetector;

    @Inject
    public DefaultTaskPropertiesService(PropertyWalker propertyWalker, FileCollectionFactory fileCollectionFactory, JvmVersionDetector jvmVersionDetector) {
        this.propertyWalker = propertyWalker;
        this.fileCollectionFactory = fileCollectionFactory;
        this.jvmVersionDetector = jvmVersionDetector;
    }

    @Override
    public TestTaskProperties collectProperties(Test task) {
        ImmutableSet.Builder<InputFileProperty> inputFileProperties = ImmutableSet.builder();
        ImmutableSet.Builder<OutputFileProperty> outputFileProperties = ImmutableSet.builder();
        TaskPropertyUtils.visitProperties(propertyWalker, task, new PropertyVisitor.Adapter() {
            @Override
            public void visitInputFileProperty(
                String propertyName,
                boolean optional,
                boolean skipWhenEmpty,
                DirectorySensitivity directorySensitivity,
                LineEndingSensitivity lineEndingSensitivity,
                boolean incremental,
                @Nullable Class<? extends FileNormalizer> fileNormalizer,
                PropertyValue value,
                InputFilePropertyType filePropertyType,
                ContentTracking contentTracking
            ) {
                FileCollection files = resolveLeniently(value);
                inputFileProperties.add(new DefaultInputFileProperty(propertyName, files));
            }

            @Override
            public void visitOutputFileProperty(
                String propertyName,
                boolean optional,
                ContentTracking contentTracking,
                PropertyValue value,
                OutputFilePropertyType filePropertyType
            ) {
                FileCollection files = resolveLeniently(value);
                OutputFileProperty.TreeType treeType = filePropertyType.getOutputType() == TreeType.DIRECTORY
                    ? OutputFileProperty.TreeType.DIRECTORY
                    : OutputFileProperty.TreeType.FILE;
                outputFileProperties.add(new DefaultOutputFileProperty(propertyName, files, treeType));
            }
        });
        return new DefaultTestTaskProperties(
            task.getOptions() instanceof JUnitPlatformOptions,
            task.getForkEvery(),
            collectFilters(task),
            collectForkOptions(task),
            task.getCandidateClassFiles(),
            inputFileProperties.build(),
            outputFileProperties.build()
        );
    }

    private FileCollection resolveLeniently(PropertyValue value) {
        Object sources = value.call();
        return sources == null ? fileCollectionFactory.empty() : fileCollectionFactory.resolvingLeniently(sources);
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
        JvmTestExecutionSpec executionSpec = task.createTestExecutionSpec();
        DefaultJavaForkOptions forkOptions = (DefaultJavaForkOptions) executionSpec.getJavaForkOptions();
        String executable = forkOptions.getExecutable();
        return new DefaultTestTaskForkOptions(
            forkOptions.getWorkingDir(),
            executable,
            detectJavaVersion(executable),
            requireNonNull(uncheckedCast(executionSpec.getClasspath())),
            requireNonNull(uncheckedCast(executionSpec.getModulePath())),
            forkOptions.getAllJvmArgs(),
            forkOptions.getActualEnvironment()
        );
    }

    private int detectJavaVersion(String executable) {
        return Integer.parseInt(jvmVersionDetector.getJavaVersion(executable).getMajorVersion());
    }
}
