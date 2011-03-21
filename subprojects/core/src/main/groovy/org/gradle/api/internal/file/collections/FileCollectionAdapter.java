/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.file.collections;

import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.Set;

/**
 * Adapts a {@link MinimalFileCollection} into a full {@link org.gradle.api.file.FileCollection}.
 */
public class FileCollectionAdapter extends AbstractFileCollection implements MinimalFileCollection, CompositeFileCollection {
    private final MinimalFileCollection delegate;

    public FileCollectionAdapter(MinimalFileCollection delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    public void resolve(FileCollectionResolveContext context) {
        context.add(delegate);
    }

    public Set<File> getFiles() {
        return GUtil.addSets(delegate);
    }
}
