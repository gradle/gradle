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
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.CompositeTaskOutputPropertySpec;
import org.gradle.api.internal.tasks.DefaultCacheableTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskPropertyUtils;
import org.gradle.api.internal.tasks.ValidatingValue;
import org.gradle.util.DeferredUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

@NonNullApi
public class GetOutputFilesVisitor extends PropertyVisitor.Adapter {
    private final List<TaskOutputFilePropertySpec> specs = Lists.newArrayList();
    private final String ownerDisplayName;
    private final FileResolver fileResolver;
    private ImmutableSortedSet<TaskOutputFilePropertySpec> fileProperties;
    private boolean hasDeclaredOutputs;

    public GetOutputFilesVisitor(String ownerDisplayName, FileResolver fileResolver) {
        this.ownerDisplayName = ownerDisplayName;
        this.fileResolver = fileResolver;
    }

    @Override
    public void visitOutputFileProperty(String propertyName, boolean optional, ValidatingValue value, OutputFilePropertyType filePropertyType) {
        hasDeclaredOutputs = true;
        if (filePropertyType == OutputFilePropertyType.DIRECTORIES || filePropertyType == OutputFilePropertyType.FILES) {
            Iterators.addAll(specs, CompositeTaskOutputPropertySpec.resolveToOutputProperties(ownerDisplayName, propertyName, value, filePropertyType.getOutputType(), fileResolver, filePropertyType.getValidationAction()));
        } else {
            File outputFile = unpackOutputFileValue(value);
            if (outputFile == null) {
                return;
            }
            DefaultCacheableTaskOutputFilePropertySpec filePropertySpec = new DefaultCacheableTaskOutputFilePropertySpec(ownerDisplayName, fileResolver, filePropertyType.getOutputType(), value, filePropertyType.getValidationAction());
            filePropertySpec.withPropertyName(propertyName);
            specs.add(filePropertySpec);
        }
    }

    @Nullable
    private File unpackOutputFileValue(ValidatingValue value) {
        Object unpackedOutput = DeferredUtil.unpack(value.call());
        if (unpackedOutput == null) {
            return null;
        }
        return fileResolver.resolve(unpackedOutput);

    }

    public ImmutableSortedSet<TaskOutputFilePropertySpec> getFileProperties() {
        if (fileProperties == null) {
            fileProperties = TaskPropertyUtils.collectFileProperties("output", specs.iterator());
        }
        return fileProperties;
    }

    public boolean hasDeclaredOutputs() {
        return hasDeclaredOutputs;
    }
}
