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

import com.google.common.base.Preconditions;
import com.google.common.hash.Hasher;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.changedetection.resources.HashContentSnapshot;
import org.gradle.api.internal.changedetection.resources.SnapshottableReadableResource;
import org.gradle.api.internal.changedetection.resources.SnapshottableResource;
import org.gradle.internal.nativeintegration.filesystem.FileType;
import org.gradle.internal.resource.ResourceContentMetadataSnapshot;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.google.common.hash.Funnels.asOutputStream;
import static com.google.common.hash.Hashing.md5;
import static com.google.common.io.ByteStreams.copy;

class ZipFileResource implements SnapshottableReadableResource {
    private final ZipInputStream input;
    private final String path;
    private boolean used;
    private HashContentSnapshot snapshot;

    public ZipFileResource(ZipInputStream input, ZipEntry zipEntry) {
        this.input = input;
        this.path = zipEntry.getName();
    }

    private void ensureUnused() {
        Preconditions.checkState(!used, "Zip file entry cannot be opened twice");
        used = true;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getName() {
        return FilenameUtils.getName(path);
    }

    @Override
    public RelativePath getRelativePath() {
        return new RelativePath(true, path);
    }

    @Override
    public FileType getType() {
        return FileType.RegularFile;
    }

    @Override
    public boolean isRoot() {
        return false;
    }

    @Override
    public InputStream read() throws IOException {
        ensureUnused();
        return new CloseShieldInputStream(input);
    }

    @Override
    public ResourceContentMetadataSnapshot getContent() {
        if (snapshot != null) {
            return snapshot;
        }
        ensureUnused();
        Hasher hasher = md5().newHasher();
        try {
            copy(input, asOutputStream(hasher));
            snapshot = new HashContentSnapshot(getType(), hasher.hash());
            return snapshot;
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Failed to hash file '%s' in Jar found on classpath", getPath()), e);
        }
    }

    @Override
    public SnapshottableResource getRoot() {
        return this;
    }

    @Override
    public Iterable<? extends SnapshottableResource> getElements() {
        return Collections.singleton(this);
    }

    @Override
    public String getDisplayName() {
        return getPath();
    }

    @Override
    public void close() throws IOException {
    }
}
