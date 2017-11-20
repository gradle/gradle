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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.AbstractFileTreeElement;
import org.gradle.internal.file.FileType;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.InputStream;

/**
 * Adapts a file snapshot to the {@link FileTreeElement} interface, e.g. to allow
 * passing it to a {@link org.gradle.api.tasks.util.PatternSet} for filtering.
 *
 * The fields on this class are prefixed with _ to avoid users from accidentally referencing them
 * in dynamic Groovy code.
 */
class SnapshotFileTreeElement extends AbstractFileTreeElement {
    private final FileSnapshot _snapshot;
    private final FileSystem _fileSystem;
    private File _file;

    SnapshotFileTreeElement(FileSnapshot snapshot, FileSystem fileSystem) {
        super(fileSystem);
        this._snapshot = snapshot;
        this._fileSystem = fileSystem;
    }
    @Override
    public String getDisplayName() {
        return "file '" + getFile() + "'";
    }

    @Override
    public File getFile() {
        if (_file == null) {
            _file = new File(_snapshot.getPath());
        }
        return _file;
    }

    @Override
    public InputStream open() {
        return GFileUtils.openInputStream(getFile());
    }

    @Override
    public boolean isDirectory() {
        return _snapshot.getType() == FileType.Directory;
    }

    @Override
    public long getLastModified() {
        return getFile().lastModified();
    }

    @Override
    public long getSize() {
        return getFile().length();
    }

    @Override
    public String getName() {
        return _snapshot.getName();
    }

    @Override
    public String getPath() {
        return _snapshot.getPath();
    }

    @Override
    public RelativePath getRelativePath() {
        return _snapshot.getRelativePath();
    }

    @Override
    public int getMode() {
        return _fileSystem.getUnixMode(getFile());
    }
}
