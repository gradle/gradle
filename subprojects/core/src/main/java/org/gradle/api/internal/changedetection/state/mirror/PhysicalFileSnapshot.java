/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state.mirror;

import com.google.common.base.Preconditions;
import org.gradle.api.internal.changedetection.state.FileHashSnapshot;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;

import java.nio.file.Path;

@SuppressWarnings("Since15")
public class PhysicalFileSnapshot implements PhysicalSnapshot {
    private final HashCode hash;
    private final long timestamp;
    private final Path path;
    private final String name;

    public PhysicalFileSnapshot(Path path, String name, long lastModified, HashCode contentMd5) {
        this.path = path;
        this.name = name;
        this.timestamp = lastModified;
        this.hash = contentMd5;
    }

    public Path getPath() {
        return path;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public FileType getType() {
        return FileType.RegularFile;
    }

    @Override
    public PhysicalSnapshot add(String[] segments, int offset, PhysicalSnapshot snapshot) {
        if (segments.length == offset) {
            Preconditions.checkState(snapshot.getClass().equals(getClass()), "Expected different snapshot type: requested %s, but was: %s", snapshot.getClass().getSimpleName(), getClass().getSimpleName());
            return this;
        }
        throw new UnsupportedOperationException("Cannot add children of file");
    }

    @Override
    public void accept(HierarchicalFileTreeVisitor visitor) {
        visitor.visit(getPath(), getName(), new FileHashSnapshot(hash, timestamp));
    }

    public HashCode getHash() {
        return hash;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
