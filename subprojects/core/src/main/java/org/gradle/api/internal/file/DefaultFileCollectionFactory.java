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

package org.gradle.api.internal.file;

import org.gradle.api.Buildable;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.FileCollectionAdapter;
import org.gradle.api.internal.file.collections.ListBackedFileSet;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public class DefaultFileCollectionFactory implements FileCollectionFactory {
    @Override
    public FileCollection create(final TaskDependency builtBy, MinimalFileSet contents) {
        if (contents instanceof Buildable) {
            throw new UnsupportedOperationException("Not implemented yet.");
        }
        return new FileCollectionAdapter(contents) {
            @Override
            public TaskDependency getBuildDependencies() {
                return builtBy;
            }
        };
    }

    @Override
    public FileCollection create(MinimalFileSet contents) {
        return new FileCollectionAdapter(contents);
    }

    @Override
    public FileCollection empty(String displayName) {
        // At some point, introduce a more efficient implementation for an empty collection
        return fixed(displayName, Collections.<File>emptyList());
    }

    @Override
    public FileCollection fixed(final String displayName, File... files) {
        return new FileCollectionAdapter(new ListBackedFileSet(files) {
            @Override
            public String getDisplayName() {
                return displayName;
            }
        });
    }

    @Override
    public FileCollection fixed(final String displayName, Collection<File> files) {
        return new FileCollectionAdapter(new ListBackedFileSet(files) {
            @Override
            public String getDisplayName() {
                return displayName;
            }
        });
    }
}
