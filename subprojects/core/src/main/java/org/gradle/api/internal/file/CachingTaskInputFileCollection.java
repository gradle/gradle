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

import com.google.common.collect.Iterables;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.internal.file.collections.DefaultFileCollectionResolveContext;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.internal.tasks.properties.LifecycleAwareValue;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A {@link org.gradle.api.file.ConfigurableFileCollection} that can be used as a task input property. Caches the matching set of files during task execution, and discards the result after task execution.
 *
 * TODO - disallow further changes to this collection once task has started
 * TODO - keep the file entries to snapshot later, to avoid a stat on each file during snapshot
 */
public class CachingTaskInputFileCollection extends DefaultConfigurableFileCollection implements LifecycleAwareValue {
    private final FileResolver fileResolver;
    private boolean canCache;
    private Set<File> cachedValue;

    // TODO - display name
    public CachingTaskInputFileCollection(FileResolver fileResolver, TaskResolver taskResolver) {
        super(fileResolver, taskResolver);
        this.fileResolver = fileResolver;
    }

    @Override
    public void visitContents(FileCollectionResolveContext context) {
        if (canCache) {
            if (cachedValue == null) {
                DefaultFileCollectionResolveContext nested = new DefaultFileCollectionResolveContext(fileResolver);
                super.visitContents(nested);
                // We use a HashSet instead of an ImmutableSet builder to work around a performance regression in Guava for large sets
                // See https://github.com/google/guava/issues/3223
                Set<File> files = new HashSet<File>();
                for (FileCollectionInternal fileCollection : nested.resolveAsFileCollections()) {
                    Iterables.addAll(files, fileCollection);
                }
                this.cachedValue = Collections.unmodifiableSet(files);
            }
            context.add(cachedValue);
        } else {
            super.visitContents(context);
        }
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
