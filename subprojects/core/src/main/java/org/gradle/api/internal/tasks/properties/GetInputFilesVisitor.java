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
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.tasks.PropertyFileCollection;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.FileNormalizer;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.properties.InputBehavior;
import org.gradle.internal.properties.InputFilePropertyType;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;

import javax.annotation.Nullable;
import java.util.List;

public class GetInputFilesVisitor implements PropertyVisitor {
    private final List<InputFilePropertySpec> specs = Lists.newArrayList();
    private final FileCollectionFactory fileCollectionFactory;
    private final String ownerDisplayName;
    private boolean hasSourceFiles;

    private ImmutableSortedSet<InputFilePropertySpec> fileProperties;

    public GetInputFilesVisitor(String ownerDisplayName, FileCollectionFactory fileCollectionFactory) {
        this.ownerDisplayName = ownerDisplayName;
        this.fileCollectionFactory = fileCollectionFactory;
    }

    @Override
    public void visitInputFileProperty(
        final String propertyName,
        boolean optional,
        InputBehavior behavior,
        DirectorySensitivity directorySensitivity,
        LineEndingSensitivity lineEndingSensitivity,
        @Nullable FileNormalizer fileNormalizer,
        PropertyValue value,
        InputFilePropertyType filePropertyType
    ) {
        FileCollectionInternal actualValue = FileParameterUtils.resolveInputFileValue(fileCollectionFactory, filePropertyType, value);
        FileNormalizer normalizer = FileParameterUtils.normalizerOrDefault(fileNormalizer);
        specs.add(new DefaultInputFilePropertySpec(
            propertyName,
            normalizer,
            new PropertyFileCollection(ownerDisplayName, propertyName, "input", actualValue),
            value,
            behavior,
            normalizeDirectorySensitivity(normalizer, directorySensitivity),
            lineEndingSensitivity
        ));
        if (behavior.shouldSkipWhenEmpty()) {
            hasSourceFiles = true;
        }
    }

    private DirectorySensitivity normalizeDirectorySensitivity(FileNormalizer normalizer, DirectorySensitivity directorySensitivity) {
        return normalizer.isIgnoringDirectories()
            ? DirectorySensitivity.DEFAULT
            : directorySensitivity;
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
