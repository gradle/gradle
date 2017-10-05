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
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionVisitor;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.util.DeferredUtil;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@NonNullApi
public class CompositeTaskOutputPropertySpec extends AbstractTaskOutputPropertySpec implements DeclaredTaskOutputFileProperty {

    private final OutputType outputType;
    private final ValidatingValue paths;
    private final ValidationAction validationAction;
    private final String taskName;
    private final FileResolver resolver;

    public CompositeTaskOutputPropertySpec(String taskName, FileResolver resolver, OutputType outputType, ValidatingValue paths, ValidationAction validationAction) {
        this.taskName = taskName;
        this.resolver = resolver;
        this.outputType = outputType;
        this.paths = paths;
        this.validationAction = validationAction;
    }

    public OutputType getOutputType() {
        return outputType;
    }

    public Iterator<TaskOutputFilePropertySpec> resolveToOutputProperties() {
        Object unpackedPaths = DeferredUtil.unpack(paths);
        if (unpackedPaths == null) {
            return Iterators.emptyIterator();
        } else if (unpackedPaths instanceof Map) {
            final Iterator<? extends Map.Entry<?, ?>> iterator = ((Map<?, ?>) unpackedPaths).entrySet().iterator();
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
            final List<Object> roots = Lists.newArrayList();
            resolver.resolveFiles(paths).visitRootElements(new FileCollectionVisitor() {
                @Override
                public void visitCollection(FileCollectionInternal fileCollection) {
                    visitRoot(fileCollection);
                }

                @Override
                public void visitTree(FileTreeInternal fileTree) {
                    visitRoot(fileTree);
                }

                @Override
                public void visitDirectoryTree(DirectoryFileTree directoryTree) {
                    visitRoot(directoryTree);
                }

                private void visitRoot(Object root) {
                    roots.add(root);
                }
            });

            final Iterator<Object> iterator = roots.iterator();
            return new AbstractIterator<TaskOutputFilePropertySpec>() {
                private int index;

                @Override
                protected TaskOutputFilePropertySpec computeNext() {
                    if (!iterator.hasNext()) {
                        return endOfData();
                    }
                    Object root = iterator.next();
                    return new NonCacheableTaskOutputPropertySpec(taskName, CompositeTaskOutputPropertySpec.this, ++index, resolver, root);
                }
            };
        }
    }

    @Override
    public void validate(TaskValidationContext context) {
        paths.validate(getPropertyName(), isOptional(), validationAction, context);
    }
}
