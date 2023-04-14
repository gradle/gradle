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

package org.gradle.api.internal.tasks.properties;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileSystemLocationProperty;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree;
import org.gradle.api.internal.tasks.PropertyFileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.MutableBoolean;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.properties.OutputFilePropertyType;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.util.internal.DeferredUtil;

import java.io.File;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class OutputUnpacker implements PropertyVisitor {

    private final String ownerDisplayName;
    private final FileCollectionFactory fileCollectionFactory;
    private final boolean locationOnly;
    private final UnpackedOutputConsumer unpackedOutputConsumer;
    private final boolean finalizeBeforeUnpacking;
    private boolean hasDeclaredOutputs;

    public OutputUnpacker(String ownerDisplayName, FileCollectionFactory fileCollectionFactory, boolean locationOnly, boolean finalizeBeforeUnpacking, UnpackedOutputConsumer unpackedOutputConsumer) {
        this.ownerDisplayName = ownerDisplayName;
        this.fileCollectionFactory = fileCollectionFactory;
        this.locationOnly = locationOnly;
        this.finalizeBeforeUnpacking = finalizeBeforeUnpacking;
        this.unpackedOutputConsumer = unpackedOutputConsumer;
    }

    public interface UnpackedOutputConsumer {
        void visitUnpackedOutputFileProperty(String propertyName, boolean optional, PropertyValue value, OutputFilePropertySpec spec);
        void visitEmptyOutputFileProperty(String propertyName, boolean optional, PropertyValue value);

        static UnpackedOutputConsumer composite(UnpackedOutputConsumer consumer1, UnpackedOutputConsumer consumer2) {
            return new UnpackedOutputConsumer() {
                @Override
                public void visitUnpackedOutputFileProperty(String propertyName, boolean optional, PropertyValue value, OutputFilePropertySpec spec) {
                    consumer1.visitUnpackedOutputFileProperty(propertyName, optional, value, spec);
                    consumer2.visitUnpackedOutputFileProperty(propertyName, optional, value, spec);
                }

                @Override
                public void visitEmptyOutputFileProperty(String propertyName, boolean optional, PropertyValue value) {
                    consumer1.visitEmptyOutputFileProperty(propertyName, optional, value);
                    consumer2.visitEmptyOutputFileProperty(propertyName, optional, value);
                }
            };
        }
    }

    @Override
    public void visitOutputFileProperty(String propertyName, boolean optional, PropertyValue value, OutputFilePropertyType filePropertyType) {
        hasDeclaredOutputs = true;
        MutableBoolean hasSpecs = new MutableBoolean();
        if (finalizeBeforeUnpacking) {
            value.maybeFinalizeValue();
        }
        resolveOutputFilePropertySpecs(ownerDisplayName, propertyName, value, filePropertyType, fileCollectionFactory, locationOnly, spec -> {
            hasSpecs.set(true);
            unpackedOutputConsumer.visitUnpackedOutputFileProperty(propertyName, optional, value, spec);
        });
        if (!hasSpecs.get()) {
            unpackedOutputConsumer.visitEmptyOutputFileProperty(propertyName, optional, value);
        }
    }

    public boolean hasDeclaredOutputs() {
        return hasDeclaredOutputs;
    }

    /**
     * Resolves the given output file property to individual property specs.
     *
     * Especially, values of type {@link Map} are resolved.
     */
    private static void resolveOutputFilePropertySpecs(
        String ownerDisplayName,
        String propertyName,
        PropertyValue value,
        OutputFilePropertyType filePropertyType,
        FileCollectionFactory fileCollectionFactory,
        boolean locationOnly,
        Consumer<OutputFilePropertySpec> consumer
    ) {
        Object unpackedValue = value.call();
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
            AtomicInteger index = new AtomicInteger();
            outputFileCollection.visitStructure(new FileCollectionStructureVisitor() {
                @Override
                public void visitCollection(FileCollectionInternal.Source source, Iterable<File> contents) {
                    for (File content : contents) {
                        FileCollectionInternal outputFiles = fileCollectionFactory.fixed(content);
                        consumer.accept(new DefaultCacheableOutputFilePropertySpec(propertyName, "$" + index.incrementAndGet(), outputFiles, outputType));
                    }
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
