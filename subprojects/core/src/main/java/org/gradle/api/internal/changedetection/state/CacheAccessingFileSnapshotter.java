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

import org.gradle.api.file.FileTreeElement;
import org.gradle.cache.CacheAccess;
import org.gradle.internal.Factory;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.resource.TextResource;

import java.io.File;

public class CacheAccessingFileSnapshotter implements FileSnapshotter {
    private final FileSnapshotter delegate;
    private final CacheAccess cacheAccess;

    public CacheAccessingFileSnapshotter(FileSnapshotter delegate, CacheAccess cacheAccess) {
        this.delegate = delegate;
        this.cacheAccess = cacheAccess;
    }

    @Override
    public FileSnapshot snapshot(final TextResource resource) {
        return cacheAccess.useCache("snapshot(TextResource)", new Factory<FileSnapshot>() {
            @Override
            public FileSnapshot create() {
                return delegate.snapshot(resource);
            }
        });
    }

    @Override
    public FileSnapshot snapshot(final File file) {
        return cacheAccess.useCache("snapshot(File)", new Factory<FileSnapshot>() {
            @Override
            public FileSnapshot create() {
                return delegate.snapshot(file);
            }
        });
    }

    @Override
    public FileSnapshot snapshot(final FileTreeElement fileDetails) {
        return cacheAccess.useCache("snapshot(FileTreeElement)", new Factory<FileSnapshot>() {
            @Override
            public FileSnapshot create() {
                return delegate.snapshot(fileDetails);
            }
        });
    }

    @Override
    public HashValue hash(final File file) {
        return cacheAccess.useCache("hash(File)", new Factory<HashValue>() {
            @Override
            public HashValue create() {
                return delegate.hash(file);
            }
        });
    }
}
