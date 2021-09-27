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

package org.gradle.internal.tasks.impl;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.tasks.TaskPropertyUtils;
import org.gradle.api.internal.tasks.properties.ContentTracking;
import org.gradle.api.internal.tasks.properties.InputFilePropertyType;
import org.gradle.api.internal.tasks.properties.OutputFilePropertyType;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.tasks.InputFileProperty;
import org.gradle.internal.tasks.OutputFileProperty;
import org.gradle.internal.tasks.TaskProperties;
import org.gradle.internal.tasks.TaskPropertiesService;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Set;

import static java.util.Collections.emptySet;

public class DefaultTaskPropertiesService implements TaskPropertiesService {

    private final PropertyWalker propertyWalker;
    private final FileCollectionFactory fileCollectionFactory;

    @Inject
    public DefaultTaskPropertiesService(PropertyWalker propertyWalker, FileCollectionFactory fileCollectionFactory) {
        this.propertyWalker = propertyWalker;
        this.fileCollectionFactory = fileCollectionFactory;
    }

    @Override
    public TaskProperties collectProperties(Task task) {
        ImmutableSet.Builder<InputFileProperty> inputFiles = ImmutableSet.builder();
        ImmutableSet.Builder<OutputFileProperty> outputFiles = ImmutableSet.builder();
        TaskPropertyUtils.visitProperties(propertyWalker, (TaskInternal) task, new PropertyVisitor.Adapter() {
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
                Set<File> files = resolveLeniently(value);
                inputFiles.add(new DefaultInputFileProperty(propertyName, files));
            }

            @Override
            public void visitOutputFileProperty(
                String propertyName,
                boolean optional,
                ContentTracking contentTracking,
                PropertyValue value,
                OutputFilePropertyType filePropertyType
            ) {
                Set<File> files = resolveLeniently(value);
                OutputFileProperty.TreeType treeType = filePropertyType.getOutputType() == TreeType.DIRECTORY
                    ? OutputFileProperty.TreeType.DIRECTORY
                    : OutputFileProperty.TreeType.FILE;
                outputFiles.add(new DefaultOutputFileProperty(propertyName, files, treeType));
            }
        });
        return new DefaultTaskProperties(inputFiles.build(), outputFiles.build());
    }

    private Set<File> resolveLeniently(PropertyValue value) {
        Object sources = value.call();
        return sources == null ? emptySet() : fileCollectionFactory.resolvingLeniently(sources).getFiles();
    }
}
