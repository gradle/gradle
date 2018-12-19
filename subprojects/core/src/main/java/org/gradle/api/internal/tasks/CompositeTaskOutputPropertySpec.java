/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionLeafVisitor;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.MutableBoolean;
import org.gradle.internal.file.TreeType;
import org.gradle.util.DeferredUtil;

import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@NonNullApi
public class CompositeTaskOutputPropertySpec extends AbstractTaskOutputPropertySpec implements DeclaredTaskOutputFileProperty {

    private final TreeType outputType;
    private final ValidatingValue value;
    private final ValidationAction validationAction;
    private final String taskDisplayName;
    private final FileResolver resolver;

    public CompositeTaskOutputPropertySpec(String taskDisplayName, FileResolver resolver, TreeType outputType, ValidatingValue value, ValidationAction validationAction) {
        this.taskDisplayName = taskDisplayName;
        this.resolver = resolver;
        this.outputType = outputType;
        this.value = value;
        this.validationAction = validationAction;
    }

    public TreeType getOutputType() {
        return outputType;
    }

    public Iterator<TaskOutputFilePropertySpec> resolveToOutputProperties() {
        Object unpackedValue = DeferredUtil.unpack(value);
        if (unpackedValue == null) {
            return Collections.emptyIterator();
        } else if (unpackedValue instanceof Map) {
            final Iterator<? extends Map.Entry<?, ?>> iterator = ((Map<?, ?>) unpackedValue).entrySet().iterator();
            return new AbstractIterator<TaskOutputFilePropertySpec>() {
                @Override
                protected TaskOutputFilePropertySpec computeNext() {
                    if (iterator.hasNext()) {
                        Map.Entry<?, ?> entry = iterator.next();
                        Object key = entry.getKey();
                        if (key == null) {
                            throw new IllegalArgumentException(String.format("Mapped output property '%s' has null key", getPropertyName()));
                        }
                        String id = key.toString();
                        File file = resolver.resolve(entry.getValue());
                        return new CacheableTaskOutputCompositeFilePropertyElementSpec(CompositeTaskOutputPropertySpec.this, "." + id, file);
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
                return Iterators.<TaskOutputFilePropertySpec>singletonIterator(CompositeTaskOutputPropertySpec.this);
            } else {
                final Iterator<File> iterator = roots.iterator();
                return new AbstractIterator<TaskOutputFilePropertySpec>() {
                    private int index;

                    @Override
                    protected TaskOutputFilePropertySpec computeNext() {
                        if (!iterator.hasNext()) {
                            return endOfData();
                        }
                        return new CacheableTaskOutputCompositeFilePropertyElementSpec(CompositeTaskOutputPropertySpec.this, "$" + (++index), iterator.next());
                    }
                };
            }
        }
    }

    @Override
    public void attachProducer(Task producer) {
        value.attachProducer(producer);
    }

    @Override
    public void prepareValue() {
        value.maybeFinalizeValue();
    }

    @Override
    public void cleanupValue() {
    }

    @Override
    public void validate(TaskValidationContext context) {
        value.validate(getPropertyName(), isOptional(), validationAction, context);
    }

    @Override
    public FileCollection getPropertyFiles() {
        return new TaskPropertyFileCollection(taskDisplayName, "output", this, resolver, value);
    }
}
