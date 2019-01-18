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
import org.gradle.api.internal.tasks.PropertyFileCollection;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.file.PathToFileResolver;

import java.util.List;

public class GetInputFilesVisitor extends PropertyVisitor.Adapter {
    private final List<InputFilePropertySpec> specs = Lists.newArrayList();
    private final PathToFileResolver resolver;
    private final String ownerDisplayName;
    private boolean hasSourceFiles;

    private ImmutableSortedSet<InputFilePropertySpec> fileProperties;

    public GetInputFilesVisitor(String ownerDisplayName, PathToFileResolver fileResolver) {
        this.resolver = fileResolver;
        this.ownerDisplayName = ownerDisplayName;
    }

    @Override
    public void visitInputFileProperty(final String propertyName, boolean optional, boolean skipWhenEmpty, Class<? extends FileNormalizer> fileNormalizer, PropertyValue value, InputFilePropertyType filePropertyType) {
        Object actualValue = FileParameterUtils.resolveInputFileValue(resolver, filePropertyType, value);
        specs.add(new DefaultInputFilePropertySpec(
            propertyName,
            fileNormalizer,
            new PropertyFileCollection(ownerDisplayName, propertyName, "input", resolver, actualValue),
            skipWhenEmpty));
        if (skipWhenEmpty) {
            hasSourceFiles = true;
        }
    }

    public ImmutableSortedSet<InputFilePropertySpec> getFileProperties() {
        if (fileProperties == null) {
            fileProperties = FileParameterUtils.collectFileProperties("input", specs.iterator());
        }
        return fileProperties;
    }

    public boolean hasSourceFiles() {
        return hasSourceFiles;
    }
}
