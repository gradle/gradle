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

import org.gradle.api.internal.changedetection.state.FileHashSnapshot;
import org.gradle.internal.file.FileType;

/**
 * A file snapshot for a regular file.
 */
public class PhysicalFileSnapshot extends AbstractPhysicalSnapshot implements MutablePhysicalSnapshot {
    private final FileHashSnapshot content;

    public PhysicalFileSnapshot(String absolutePath, String name, FileHashSnapshot content) {
        super(absolutePath, name);
        this.content = content;
    }

    @Override
    public FileType getType() {
        return FileType.RegularFile;
    }

    /**
     * The content hash and timestamp of the file.
     */
    public FileHashSnapshot getContent() {
        return content;
    }

    @Override
    public void accept(PhysicalSnapshotVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * {@link PhysicalFileSnapshot} is a {@link MutablePhysicalSnapshot}, since one can try to add
     * a child to it. This method then checks if the to be added child is also a {@link PhysicalFileSnapshot}
     * and then discards the snapshot to be added. In any other case, an exception is thrown.
     * </p>
     *
     */
    @Override
    public MutablePhysicalSnapshot add(String[] segments, int offset, MutablePhysicalSnapshot snapshot) {
        if (segments.length == offset) {
            if (snapshot.getType() != getType()) {
                throw new IllegalStateException(String.format("Expected different snapshot type: requested %s, but was: %s", snapshot.getType(), getType()));
            }
            return this;
        }
        throw new UnsupportedOperationException("Cannot add children of file");
    }
}
