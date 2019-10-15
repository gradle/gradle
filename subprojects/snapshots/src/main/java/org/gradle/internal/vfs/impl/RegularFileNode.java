/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.vfs.impl;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;

import javax.annotation.Nonnull;

public class RegularFileNode extends AbstractSnapshotNode {
    private final RegularFileSnapshot snapshot;
    private final Node parent;

    public RegularFileNode(Node parent, RegularFileSnapshot snapshot) {
        this.snapshot = snapshot;
        this.parent = parent;
    }

    @Nonnull
    @Override
    public Node getDescendant(ImmutableList<String> path) {
        return getMissingDescendant(path);
    }

    @Override
    public Node replaceDescendant(ImmutableList<String> path, ChildNodeSupplier nodeSupplier) {
        return getMissingDescendant(path);
    }

    @Override
    public void removeDescendant(ImmutableList<String> path) {
        parent.removeDescendant(ImmutableList.of(snapshot.getName()));
    }

    @Override
    public String getAbsolutePath() {
        return snapshot.getAbsolutePath();
    }

    @Override
    public Type getType() {
        return Type.FILE;
    }

    @Override
    public FileSystemLocationSnapshot getSnapshot() {
        return snapshot;
    }
}
