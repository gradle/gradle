/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.properties;

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.tasks.TaskInputFilePropertySpec;

import java.util.Set;

public class GetInputFilesVisitor extends PropertyVisitor.Adapter {
    private final String beanName;
    private ImmutableSortedSet.Builder<TaskInputFilePropertySpec> builder = ImmutableSortedSet.naturalOrder();
    private Set<String> names = Sets.newHashSet();
    private boolean hasSourceFiles;

    private ImmutableSortedSet<TaskInputFilePropertySpec> fileProperties;

    public GetInputFilesVisitor(String beanName) {
        this.beanName = beanName;
    }

    @Override
    public void visitInputFileProperty(TaskInputFilePropertySpec inputFileProperty) {
        String propertyName = inputFileProperty.getPropertyName();
        if (!names.add(propertyName)) {
            throw new IllegalArgumentException(String.format("Multiple %s file properties with name '%s'", "input", propertyName));
        }
        builder.add(inputFileProperty);
        if (inputFileProperty.isSkipWhenEmpty()) {
            hasSourceFiles = true;
        }
    }

    public ImmutableSortedSet<TaskInputFilePropertySpec> getFileProperties() {
        if (fileProperties == null) {
            fileProperties = builder.build();
        }
        return fileProperties;
    }

    public FileCollection getFiles() {
        return new CompositeFileCollection() {
            @Override
            public String getDisplayName() {
                return beanName + " input files";
            }

            @Override
            public void visitContents(FileCollectionResolveContext context) {
                for (TaskInputFilePropertySpec filePropertySpec : getFileProperties()) {
                    context.add(filePropertySpec.getPropertyFiles());
                }
            }
        };
    }

    public FileCollection getSourceFiles() {
        return new CompositeFileCollection() {
            @Override
            public String getDisplayName() {
                return beanName + " source files";
            }

            @Override
            public void visitContents(FileCollectionResolveContext context) {
                for (TaskInputFilePropertySpec filePropertySpec : getFileProperties()) {
                    if (filePropertySpec.isSkipWhenEmpty()) {
                        context.add(filePropertySpec.getPropertyFiles());
                    }
                }
            }
        };
    }

    public boolean hasSourceFiles() {
        return hasSourceFiles;
    }
}
