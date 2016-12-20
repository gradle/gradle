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
package org.gradle.api.internal.changedetection.state;

import com.google.common.hash.HashCode;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;

public abstract class AbstractSingleHasherFileCollectionSnapshotter extends AbstractFileCollectionSnapshotter {
    private final FileHasher hasher;

    public AbstractSingleHasherFileCollectionSnapshotter(FileHasher hasher, StringInterner stringInterner, FileSystem fileSystem, DirectoryFileTreeFactory directoryFileTreeFactory) {
        super(stringInterner, fileSystem, directoryFileTreeFactory);
        this.hasher = hasher;
    }

    @Override
    protected HashCode doHash(DefaultFileDetails fileDetails, TaskExecution current) {
        return hasher.hash(fileDetails.details);
    }
}
