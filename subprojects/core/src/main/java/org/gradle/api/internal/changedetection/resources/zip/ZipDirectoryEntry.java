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

package org.gradle.api.internal.changedetection.resources.zip;

import org.apache.commons.io.FilenameUtils;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.changedetection.resources.SnapshottableDirectoryResource;
import org.gradle.api.internal.changedetection.state.DirContentSnapshot;
import org.gradle.internal.nativeintegration.filesystem.FileType;
import org.gradle.internal.resource.ResourceContentMetadataSnapshot;

import java.util.zip.ZipEntry;

class ZipDirectoryEntry implements SnapshottableDirectoryResource {
    private final String name;

    public ZipDirectoryEntry(ZipEntry zipEntry) {
        this.name = zipEntry.getName();
    }

    @Override
    public String getPath() {
        return name.substring(0, name.length() - 1);
    }

    @Override
    public String getName() {
        return FilenameUtils.getName(getPath());
    }

    @Override
    public RelativePath getRelativePath() {
        return new RelativePath(false, getPath());
    }

    @Override
    public FileType getType() {
        return FileType.Directory;
    }

    @Override
    public boolean isRoot() {
        return false;
    }

    @Override
    public ResourceContentMetadataSnapshot getContent() {
        return DirContentSnapshot.getInstance();
    }

    @Override
    public String getDisplayName() {
        return getPath();
    }
}
