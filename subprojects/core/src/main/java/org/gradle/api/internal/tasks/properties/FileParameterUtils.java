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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileSystemLocationProperty;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree;
import org.gradle.api.internal.tasks.PropertyFileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.AbsolutePathInputNormalizer;
import org.gradle.internal.fingerprint.IgnoredPathInputNormalizer;
import org.gradle.internal.fingerprint.NameOnlyInputNormalizer;
import org.gradle.internal.fingerprint.RelativePathInputNormalizer;
import org.gradle.util.internal.DeferredUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

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

    /**
     * Resolves the given output file property to individual property specs.
     *
     * Especially, values of type {@link Map} are resolved.
     */
    public static void resolveOutputFilePropertySpecs(
        String ownerDisplayName,
        String propertyName,
        PropertyValue value,
        OutputFilePropertyType filePropertyType,
        FileCollectionFactory fileCollectionFactory,
        boolean locationOnly,
        Consumer<OutputFilePropertySpec> consumer
    ) {
        Object unpackedValue = value.getUnprocessedValue();
        unpackedValue = DeferredUtil.unpackNestableDeferred(unpackedValue);
        if (locationOnly && unpackedValue instanceof FileSystemLocationProperty) {
            unpackedValue = ((FileSystemLocationProperty<?>) unpackedValue).getLocationOnly();
        }
        if (unpackedValue instanceof Provider) {
            unpackedValue = ((Provider<?>) unpackedValue).getOrNull();
        }
        if (unpackedValue == null) {
            return;
        }
        // From here on, we already unpacked providers, so we can fail if any of the file collections contains a provider which is not present.
        if (filePropertyType == OutputFilePropertyType.DIRECTORIES || filePropertyType == OutputFilePropertyType.FILES) {
            resolveCompositeOutputFilePropertySpecs(ownerDisplayName, propertyName, unpackedValue, filePropertyType.getOutputType(), fileCollectionFactory, consumer);
        } else {
            FileCollectionInternal outputFiles = fileCollectionFactory.resolving(unpackedValue);
            DefaultCacheableOutputFilePropertySpec filePropertySpec = new DefaultCacheableOutputFilePropertySpec(propertyName, null, outputFiles, filePropertyType.getOutputType());
            consumer.accept(filePropertySpec);
        }
    }

    private static void resolveCompositeOutputFilePropertySpecs(final String ownerDisplayName, final String propertyName, Object unpackedValue, final TreeType outputType, FileCollectionFactory fileCollectionFactory, Consumer<OutputFilePropertySpec> consumer) {
        if (unpackedValue instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) unpackedValue).entrySet()) {
                Object key = entry.getKey();
                if (key == null) {
                    throw new IllegalArgumentException(String.format("Mapped output property '%s' has null key", propertyName));
                }
                String id = key.toString();
                FileCollectionInternal outputFiles = fileCollectionFactory.resolving(entry.getValue());
                consumer.accept(new DefaultCacheableOutputFilePropertySpec(propertyName, "." + id, outputFiles, outputType));
            }
        } else {
            FileCollectionInternal outputFileCollection = fileCollectionFactory.resolving(unpackedValue);
            AtomicInteger index = new AtomicInteger(0);
            outputFileCollection.visitStructure(new FileCollectionStructureVisitor() {
                @Override
                public void visitCollection(FileCollectionInternal.Source source, Iterable<File> contents) {
                    for (File content : contents) {
                        FileCollectionInternal outputFiles = fileCollectionFactory.fixed(content);
                        consumer.accept(new DefaultCacheableOutputFilePropertySpec(propertyName, "$" + index.incrementAndGet(), outputFiles, outputType));
                    }
                }

                @Override
                public void visitGenericFileTree(FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                    failOnInvalidOutputType(fileTree);
                }

                @Override
                public void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                    failOnInvalidOutputType(fileTree);
                }

                @Override
                public void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
                    // We could support an unfiltered DirectoryFileTree here as a cacheable root,
                    // but because @OutputDirectory also doesn't support it we choose not to.
                    consumer.accept(new DirectoryTreeOutputFilePropertySpec(
                        propertyName + "$" + index.incrementAndGet(),
                        new PropertyFileCollection(ownerDisplayName, propertyName, "output", fileTree),
                        root
                    ));
                }
            });
        }
    }

    private static void failOnInvalidOutputType(FileTreeInternal fileTree) {
        throw new InvalidUserDataException(String.format(
            "Only files and directories can be registered as outputs (was: %s)",
            fileTree
        ));
    }
}
