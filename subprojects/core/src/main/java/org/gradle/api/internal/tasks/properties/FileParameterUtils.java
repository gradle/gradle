/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.internal.fingerprint.AbsolutePathInputNormalizer;
import org.gradle.internal.fingerprint.IgnoredPathInputNormalizer;
import org.gradle.internal.fingerprint.NameOnlyInputNormalizer;
import org.gradle.internal.fingerprint.RelativePathInputNormalizer;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Set;

public class FileParameterUtils {

    public static Class<? extends FileNormalizer> determineNormalizerForPathSensitivity(PathSensitivity pathSensitivity) {
        switch (pathSensitivity) {
            case NONE:
                return IgnoredPathInputNormalizer.class;
            case NAME_ONLY:
                return NameOnlyInputNormalizer.class;
            case RELATIVE:
                return RelativePathInputNormalizer.class;
            case ABSOLUTE:
                return AbsolutePathInputNormalizer.class;
            default:
                throw new IllegalArgumentException("Unknown path sensitivity: " + pathSensitivity);
        }
    }

    public static Class<? extends FileNormalizer> normalizerOrDefault(@Nullable Class<? extends FileNormalizer> fileNormalizer) {
        // If this default is ever changed, ensure the documentation on PathSensitive is updated as well as this guide:
        // https://docs.gradle.org/current/userguide/build_cache_concepts.html#relocatability
        return fileNormalizer == null ? AbsolutePathInputNormalizer.class : fileNormalizer;
    }

    /**
     * Collects property specs in a sorted set to ensure consistent ordering.
     *
     * @throws IllegalArgumentException if there are multiple properties declared with the same name.
     */
    public static <T extends FilePropertySpec> ImmutableSortedSet<T> collectFileProperties(String displayName, Iterator<? extends T> fileProperties) {
        Set<String> names = Sets.newHashSet();
        ImmutableSortedSet.Builder<T> builder = ImmutableSortedSet.naturalOrder();
        while (fileProperties.hasNext()) {
            T propertySpec = fileProperties.next();
            String propertyName = propertySpec.getPropertyName();
            if (!names.add(propertyName)) {
                throw new IllegalArgumentException(String.format("Multiple %s file properties with name '%s'", displayName, propertyName));
            }
            builder.add(propertySpec);
        }
        return builder.build();
    }

    /**
     * Resolves the actual value for an input file.
     *
     * The value is the file tree rooted at the provided path for an input directory, and the provided path otherwise.
     */
    public static FileCollectionInternal resolveInputFileValue(FileCollectionFactory fileCollectionFactory, InputFilePropertyType inputFilePropertyType, Object path) {
        FileCollectionInternal fileCollection = fileCollectionFactory.resolvingLeniently(path);
        return inputFilePropertyType == InputFilePropertyType.DIRECTORY
            ? fileCollection.getAsFileTree()
            : fileCollection;
    }
}
