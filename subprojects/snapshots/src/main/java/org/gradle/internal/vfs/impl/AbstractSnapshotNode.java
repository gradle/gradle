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
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;

import javax.annotation.Nonnull;

public abstract class AbstractSnapshotNode implements Node {

    @Nonnull
    @Override
    public abstract Node getChild(ImmutableList<String> path);

    protected Node getMissingChild(ImmutableList<String> path) {
        if (path.isEmpty()) {
            return this;
        }
        String childName = path.get(0);
        return new MissingFileNode(this, getChildAbsolutePath(childName), childName).getMissingChild(path.subList(1, path.size()));
    }

    public static AbstractSnapshotNode convertToNode(FileSystemLocationSnapshot snapshot, Node parent) {
        switch (snapshot.getType()) {
            case RegularFile:
                return new RegularFileNode(parent, (RegularFileSnapshot) snapshot);
            case Directory:
                return new CompleteDirectoryNode(parent, (DirectorySnapshot) snapshot);
            case Missing:
                return new MissingFileNode(parent, snapshot.getAbsolutePath(), snapshot.getName());
            default:
                throw new AssertionError("Unknown type: " + snapshot.getType());
        }
    }

}
