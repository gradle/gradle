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

package org.gradle.api.internal.file;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.internal.file.collections.DefaultFileCollectionResolveContext;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.internal.tasks.properties.LifecycleAwareValue;

import java.io.File;

/**
 * A {@link org.gradle.api.file.ConfigurableFileCollection} that can be used as a task input property. Caches the matching set of files during task execution, and discards the result after task execution.
 *
 * TODO - disallow further changes to this collection once task has started
 * TODO - keep the file entries to snapshot later, to avoid a stat on each file during snapshot
 */
public class CachingTaskInputFileCollection extends DefaultConfigurableFileCollection implements LifecycleAwareValue {
    private final FileResolver fileResolver;
    private boolean canCache;
    private ImmutableSet<File> cachedValue;

    // TODO - display name
    public CachingTaskInputFileCollection(FileResolver fileResolver, TaskResolver taskResolver) {
        super(fileResolver, taskResolver);
        this.fileResolver = fileResolver;
    }

    @Override
    public boolean visitContents(CancellableVisitor<File> visitor) {
        if (canCache) {
            for (File file : getCachedValue()) {
                if (!visitor.accept(file)) {
                    return false;
                }
            }
            return true;
        } else {
            return super.visitContents(visitor);
        }
    }

    @Override
    public void visitContents(FileCollectionResolveContext context) {
        if (canCache) {
            context.add(getCachedValue());
        } else {
            super.visitContents(context);
        }
    }

    private ImmutableSet<File> getCachedValue() {
        if (cachedValue == null) {
            DefaultFileCollectionResolveContext nested = new DefaultFileCollectionResolveContext(fileResolver);
            super.visitContents(nested);
            final ImmutableSet.Builder<File> files = ImmutableSet.builder();
            for (FileCollectionInternal fileCollection : nested.resolveAsFileCollections()) {
                fileCollection.visitContents(new CancellableVisitor<File>() {
                    @Override
                    public boolean accept(File file) {
                        files.add(file);
                        return true;
                    }
                });
            }
            this.cachedValue = files.build();
        }
        return cachedValue;
    }

    @Override
    public void prepareValue() {
        canCache = true;
    }

    @Override
    public void cleanupValue() {
        // Keep the files and discard the origin values instead?
        canCache = false;
        cachedValue = null;
    }
}
