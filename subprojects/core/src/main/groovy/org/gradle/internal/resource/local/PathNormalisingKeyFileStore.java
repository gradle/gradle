/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.resource.local;

import org.gradle.api.Action;

import java.io.File;
import java.util.Set;

public class PathNormalisingKeyFileStore implements FileStore<String>, FileStoreSearcher<String> {

    private final PathKeyFileStore delegate;

    public PathNormalisingKeyFileStore(File baseDir) {
        this(new PathKeyFileStore(baseDir));
    }

    public PathNormalisingKeyFileStore(PathKeyFileStore delegate) {
        this.delegate = delegate;
    }

    public LocallyAvailableResource move(String key, File source) {
        return delegate.move(normalizePath(key), source);
    }

    public LocallyAvailableResource copy(String key, File source) {
        return delegate.copy(key, source);
    }

    protected String normalizePath(String path) {
        return path.replaceAll("[^\\d\\w\\./]", "_");
    }

    protected String normalizeSearchPath(String path) {
        return path.replaceAll("[^\\d\\w\\.\\*/]", "_");
    }

    public void moveFilestore(File destination) {
        delegate.moveFilestore(destination);
    }

    public LocallyAvailableResource add(String key, Action<File> addAction) {
        return delegate.add(normalizePath(key), addAction);
    }

    public Set<? extends LocallyAvailableResource> search(String key) {
        return delegate.search(normalizeSearchPath(key));
    }
}
