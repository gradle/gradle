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

import org.apache.commons.io.FilenameUtils;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.changedetection.resources.SnapshottableResource;

import java.io.IOException;
import java.util.List;

/**
 * Represents the state of a directory tree.
 */
public class DefaultSnapshottableDirectoryTree implements SnapshottableDirectoryTree {
    // Interned path
    private final String path;
    // All descendants, not just direct children
    private final List<SnapshottableFileSystemResource> descendants;

    public DefaultSnapshottableDirectoryTree(String path, List<SnapshottableFileSystemResource> descendants) {
        this.path = path;
        this.descendants = descendants;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public List<SnapshottableFileSystemResource> getDescendants() {
        return descendants;
    }

    @Override
    public SnapshottableResource getRoot() {
        return new SnapshottableFileSystemDirectory(path, new RelativePath(false, FilenameUtils.getName(path)), true);
    }

    @Override
    public void close() throws IOException {
    }
}
