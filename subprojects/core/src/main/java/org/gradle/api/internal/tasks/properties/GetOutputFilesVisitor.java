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

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionLeafVisitor;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.tasks.PropertyFileCollection;
import org.gradle.api.internal.tasks.TaskPropertyUtils;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.MutableBoolean;
import org.gradle.internal.file.TreeType;
import org.gradle.util.DeferredUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@NonNullApi
public class GetOutputFilesVisitor extends PropertyVisitor.Adapter {
    private final List<OutputFilePropertySpec> specs = Lists.newArrayList();
    private final String ownerDisplayName;
    private final FileResolver fileResolver;
    private ImmutableSortedSet<OutputFilePropertySpec> fileProperties;
    private boolean hasDeclaredOutputs;

    public GetOutputFilesVisitor(String ownerDisplayName, FileResolver fileResolver) {
        this.ownerDisplayName = ownerDisplayName;
        this.fileResolver = fileResolver;
    }

    @Override
    public void visitOutputFileProperty(String propertyName, boolean optional, ValidatingValue value, OutputFilePropertyType filePropertyType) {
        hasDeclaredOutputs = true;
        if (filePropertyType == OutputFilePropertyType.DIRECTORIES || filePropertyType == OutputFilePropertyType.FILES) {
            Iterators.addAll(specs, resolveToOutputFilePropertySpecs(ownerDisplayName, propertyName, value, filePropertyType.getOutputType(), fileResolver));
        } else {
            File outputFile = unpackOutputFileValue(value);
            if (outputFile == null) {
                return;
            }
            DefaultCacheableOutputFilePropertySpec filePropertySpec = new DefaultCacheableOutputFilePropertySpec(propertyName, null, outputFile, filePropertyType.getOutputType());
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

    public ImmutableSortedSet<OutputFilePropertySpec> getFileProperties() {
        if (fileProperties == null) {
            fileProperties = TaskPropertyUtils.collectFileProperties("output", specs.iterator());
        }
        return fileProperties;
    }

    public boolean hasDeclaredOutputs() {
        return hasDeclaredOutputs;
    }

    private static Iterator<OutputFilePropertySpec> resolveToOutputFilePropertySpecs(final String ownerDisplayName, final String propertyName, ValidatingValue value, final TreeType outputType, final FileResolver resolver) {
        Object unpackedValue = DeferredUtil.unpack(value);
        if (unpackedValue == null) {
            return Collections.emptyIterator();
        } else if (unpackedValue instanceof Map) {
            final Iterator<? extends Map.Entry<?, ?>> iterator = ((Map<?, ?>) unpackedValue).entrySet().iterator();
            return new AbstractIterator<OutputFilePropertySpec>() {
                @Override
                protected OutputFilePropertySpec computeNext() {
                    if (iterator.hasNext()) {
                        Map.Entry<?, ?> entry = iterator.next();
                        Object key = entry.getKey();
                        if (key == null) {
                            throw new IllegalArgumentException(String.format("Mapped output property '%s' has null key", propertyName));
                        }
                        String id = key.toString();
                        File file = resolver.resolve(entry.getValue());
                        return new DefaultCacheableOutputFilePropertySpec(propertyName, "." + id, file, outputType);
                    }
                    return endOfData();
                }
            };
        } else {
            final List<File> roots = Lists.newArrayList();
            final MutableBoolean nonFileRoot = new MutableBoolean();
            FileCollectionInternal outputFileCollection = resolver.resolveFiles(unpackedValue);
            outputFileCollection.visitLeafCollections(new FileCollectionLeafVisitor() {
                @Override
                public void visitCollection(FileCollectionInternal fileCollection) {
                    Iterables.addAll(roots, fileCollection);
                }

                @Override
                public void visitGenericFileTree(FileTreeInternal fileTree) {
                    nonFileRoot.set(true);
                }

                @Override
                public void visitFileTree(File root, PatternSet patterns) {
                    // We could support an unfiltered DirectoryFileTree here as a cacheable root,
                    // but because @OutputDirectory also doesn't support it we choose not to.
                    nonFileRoot.set(true);
                }
            });

            if (nonFileRoot.get()) {
                return Iterators.<OutputFilePropertySpec>singletonIterator(new CompositeOutputFilePropertySpec(
                    propertyName,
                    new PropertyFileCollection(ownerDisplayName, propertyName, "output", resolver, value),
                    outputType)
                );
            } else {
                final Iterator<File> iterator = roots.iterator();
                return new AbstractIterator<OutputFilePropertySpec>() {
                    private int index;

                    @Override
                    protected OutputFilePropertySpec computeNext() {
                        if (!iterator.hasNext()) {
                            return endOfData();
                        }
                        return new DefaultCacheableOutputFilePropertySpec(propertyName, "$" + (++index), iterator.next(), outputType);
                    }
                };
            }
        }
    }

}
