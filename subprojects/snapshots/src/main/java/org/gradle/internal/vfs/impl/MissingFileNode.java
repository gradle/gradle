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
import org.gradle.internal.snapshot.MissingFileSnapshot;

import javax.annotation.Nonnull;

public class MissingFileNode extends AbstractSnapshotNode {

    private final Node parent;
    private final String absolutePath;
    private final String name;

    public MissingFileNode(Node parent, String absolutePath, String name) {
        this.parent = parent;
        this.absolutePath = absolutePath;
        this.name = name;
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
        parent.removeDescendant(ImmutableList.of(name));
    }

    @Override
    public String getAbsolutePath() {
        return absolutePath;
    }

    @Override
    public Type getType() {
        return Type.MISSING;
    }

    @Override
    public FileSystemLocationSnapshot getSnapshot() {
        return new MissingFileSnapshot(absolutePath, name);
    }

}
