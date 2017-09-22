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

import org.gradle.api.file.RelativePath;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;

/**
 * Snapshot for a regular file.
 */
public class RegularFileSnapshot implements FileSnapshot {
    private final String path;
    private final RelativePath relativePath;
    private final boolean root;
    private final FileContentSnapshot content;

    public RegularFileSnapshot(String path, RelativePath relativePath, boolean root, FileContentSnapshot content) {
        this.path = path;
        this.relativePath = relativePath;
        this.root = root;
        this.content = content;
    }

    @Override
    public String toString() {
        return getType() + " " + path;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getName() {
        return relativePath.getLastName();
    }

    @Override
    public boolean isRoot() {
        return root;
    }

    @Override
    public RelativePath getRelativePath() {
        return relativePath;
    }

    @Override
    public FileContentSnapshot getContent() {
        return content;
    }

    @Override
    public FileType getType() {
        return FileType.RegularFile;
    }

    @Override
    public RegularFileSnapshot withContentHash(HashCode contentHash) {
        if (!contentHash.equals(getContent().getContentMd5())) {
            return new RegularFileSnapshot(path, relativePath, root, new FileHashSnapshot(contentHash));
        }
        return this;
    }
}
