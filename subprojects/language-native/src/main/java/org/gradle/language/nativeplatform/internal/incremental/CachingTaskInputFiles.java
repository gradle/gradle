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

package org.gradle.language.nativeplatform.internal.incremental;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.internal.file.collections.DefaultFileCollectionResolveContext;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.tasks.LifecycleAwareTaskProperty;
import org.gradle.api.internal.tasks.TaskResolver;

import java.io.File;

/**
 * TODO - move this to TaskFileVarFactory
 * TODO - disallow further changes to this collection once task has started
 * TODO - keep the file entries to snapshot later, to avoid a stat on each file during snapshotting
 */
public class CachingTaskInputFiles extends DefaultConfigurableFileCollection implements LifecycleAwareTaskProperty {
    private final String taskPath;
    private final FileResolver fileResolver;
    private boolean canCache;
    private ImmutableSet<File> cachedValue;

    // TODO - display name
    public CachingTaskInputFiles(String taskPath, FileResolver fileResolver, TaskResolver taskResolver) {
        super(fileResolver, taskResolver);
        this.taskPath = taskPath;
        this.fileResolver = fileResolver;
    }

    @Override
    public void visitContents(FileCollectionResolveContext context) {
        if (canCache) {
            if (cachedValue == null) {
                DefaultFileCollectionResolveContext nested = new DefaultFileCollectionResolveContext(fileResolver);
                super.visitContents(nested);
                ImmutableSet.Builder<File> files = ImmutableSet.builder();
                for (FileCollectionInternal fileCollection : nested.resolveAsFileCollections()) {
                    files.addAll(fileCollection.getFiles());
                }
                this.cachedValue = files.build();
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
        // TODO - keep the files and discard the origin values instead?
        canCache = false;
        cachedValue = null;
    }
}
