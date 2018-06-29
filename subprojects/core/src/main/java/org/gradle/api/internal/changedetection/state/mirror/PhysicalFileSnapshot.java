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

/**
 * A file snapshot for a regular file.
 */
public class PhysicalFileSnapshot extends AbstractPhysicalSnapshot implements MutablePhysicalSnaphot {
    private final FileHashSnapshot content;

    public PhysicalFileSnapshot(String path, String name, FileHashSnapshot content) {
        super(path, name);
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
        visitor.visit(getPath(), getName(), content);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * {@link PhysicalFileSnapshot} is a {@link MutablePhysicalSnaphot}, since one can try to add
     * a child to it. This method then checks if the to be added child is also a {@link PhysicalFileSnapshot}
     * and then discards the snapshot to be added. In any other case, an exception is thrown.
     * </p>
     *
     */
    @Override
    public MutablePhysicalSnaphot add(String[] segments, int offset, MutablePhysicalSnaphot snapshot) {
        if (segments.length == offset) {
            Preconditions.checkState(snapshot.getClass().equals(getClass()), "Expected different snapshot type: requested %s, but was: %s", snapshot.getClass().getSimpleName(), getClass().getSimpleName());
            return this;
        }
        throw new UnsupportedOperationException("Cannot add children of file");
    }
}
