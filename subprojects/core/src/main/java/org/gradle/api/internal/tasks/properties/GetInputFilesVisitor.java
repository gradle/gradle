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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.tasks.PropertyFileCollection;
import org.gradle.api.tasks.ClasspathNormalizer;
import org.gradle.api.tasks.CompileClasspathNormalizer;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.IgnoredPathInputNormalizer;
import org.gradle.internal.fingerprint.LineEndingSensitivity;

import javax.annotation.Nullable;
import java.util.List;

public class GetInputFilesVisitor extends PropertyVisitor.Adapter {
    private static final ImmutableSet<Class<? extends FileNormalizer>> NORMALIZERS_IGNORING_DIRECTORIES = ImmutableSet.of(
        ClasspathNormalizer.class,
        CompileClasspathNormalizer.class,
        IgnoredPathInputNormalizer.class
    );

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
        boolean skipWhenEmpty,
        DirectorySensitivity directorySensitivity,
        LineEndingSensitivity lineEndingSensitivity,
        boolean incremental,
        @Nullable Class<? extends FileNormalizer> fileNormalizer,
        PropertyValue value,
        InputFilePropertyType filePropertyType
    ) {
        FileCollectionInternal actualValue = FileParameterUtils.resolveInputFileValue(fileCollectionFactory, filePropertyType, value);
        Class<? extends FileNormalizer> normalizer = FileParameterUtils.normalizerOrDefault(fileNormalizer);
        specs.add(new DefaultInputFilePropertySpec(
            propertyName,
            normalizer,
            new PropertyFileCollection(ownerDisplayName, propertyName, "input", actualValue),
            value,
            skipWhenEmpty,
            incremental,
            normalizeDirectorySensitivity(normalizer, directorySensitivity),
            lineEndingSensitivity
        ));
        if (skipWhenEmpty) {
            hasSourceFiles = true;
        }
    }

    private DirectorySensitivity normalizeDirectorySensitivity(Class<? extends FileNormalizer> fileNormalizer, DirectorySensitivity directorySensitivity) {
        return NORMALIZERS_IGNORING_DIRECTORIES.contains(fileNormalizer)
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
