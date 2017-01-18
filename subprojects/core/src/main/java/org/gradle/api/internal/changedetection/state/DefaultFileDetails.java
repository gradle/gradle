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
import org.gradle.api.file.RelativePath;
import org.gradle.internal.nativeintegration.filesystem.FileType;

class DefaultFileDetails implements FileDetails {
    final String path;
    final FileType type;
    private final RelativePath relativePath;
    private final boolean root;
    private final IncrementalFileSnapshot content;

    DefaultFileDetails(String path, RelativePath relativePath, FileType type, boolean root, IncrementalFileSnapshot content) {
        this.path = path;
        this.relativePath = relativePath;
        this.type = type;
        this.root = root;
        this.content = content;
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
    public IncrementalFileSnapshot getContent() {
        return content;
    }

    @Override
    public FileType getType() {
        return type;
    }

    @Override
    public FileDetails withContent(HashCode contentHash) {
        return new DefaultFileDetails(path, relativePath, type, root, new FileHashSnapshot(contentHash));
    }
}
