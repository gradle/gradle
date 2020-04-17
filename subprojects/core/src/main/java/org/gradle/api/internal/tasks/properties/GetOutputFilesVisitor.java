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
import com.google.common.collect.Lists;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.file.FileCollectionFactory;

import java.util.List;

@NonNullApi
public class GetOutputFilesVisitor extends PropertyVisitor.Adapter {
    private final List<OutputFilePropertySpec> specs = Lists.newArrayList();
    private final String ownerDisplayName;
    private final FileCollectionFactory fileCollectionFactory;
    private ImmutableSortedSet<OutputFilePropertySpec> fileProperties;
    private boolean hasDeclaredOutputs;

    public GetOutputFilesVisitor(String ownerDisplayName, FileCollectionFactory fileCollectionFactory) {
        this.ownerDisplayName = ownerDisplayName;
        this.fileCollectionFactory = fileCollectionFactory;
    }

    @Override
    public void visitOutputFileProperty(String propertyName, boolean optional, PropertyValue value, OutputFilePropertyType filePropertyType) {
        hasDeclaredOutputs = true;
        FileParameterUtils.resolveOutputFilePropertySpecs(ownerDisplayName, propertyName, value, filePropertyType, fileCollectionFactory, false, specs::add);
    }

    public ImmutableSortedSet<OutputFilePropertySpec> getFileProperties() {
        if (fileProperties == null) {
            fileProperties = FileParameterUtils.collectFileProperties("output", specs.iterator());
        }
        return fileProperties;
    }

    public boolean hasDeclaredOutputs() {
        return hasDeclaredOutputs;
    }

}
