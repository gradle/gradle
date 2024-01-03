/*
 * Copyright 2022 the original author or authors.
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

import java.io.File;
import java.util.Set;

final class EmptyFileCollection extends AbstractFileCollection {
    public static final FileCollectionInternal INSTANCE = new EmptyFileCollection(DEFAULT_COLLECTION_DISPLAY_NAME);

    private final String displayName;

    public EmptyFileCollection(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Set<File> getFiles() {
        return ImmutableSet.of();
    }

    @Override
    protected void visitContents(FileCollectionStructureVisitor visitor) {
    }

    @Override
    public FileTreeInternal getAsFileTree() {
        return new EmptyFileTree(FileTreeInternal.DEFAULT_TREE_DISPLAY_NAME);
    }
}
