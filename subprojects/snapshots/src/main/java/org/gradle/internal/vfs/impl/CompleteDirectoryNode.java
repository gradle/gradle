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

import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder;
import org.gradle.internal.snapshot.RegularFileSnapshot;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CompleteDirectoryNode implements Node {
    private final Map<String, FileSystemLocationSnapshot> childrenMap;
    private final Node parent;
    private final DirectorySnapshot directorySnapshot;

    public CompleteDirectoryNode(Node parent, DirectorySnapshot directorySnapshot) {
        this.parent = parent;
        this.directorySnapshot = directorySnapshot;
        this.childrenMap = directorySnapshot.getChildren().stream()
            .collect(Collectors.toMap(
                FileSystemLocationSnapshot::getName,
                Function.identity()
            ));
    }

    @Override
    public Node getOrCreateChild(String name, Function<Node, Node> nodeSupplier) {
        return getChildOrMissing(name);
    }

    @Override
    public Node replaceChild(String name, Function<Node, Node> nodeSupplier, Predicate<Node> shouldReplaceExisting) {
        FileSystemLocationSnapshot snapshot = childrenMap.get(name);
        if (snapshot == null) {
            return new MissingFileNode(this, getChildAbsolutePath(name), name);
        }
        Node currentChild = convertToNode(snapshot, this);
        if (!shouldReplaceExisting.test(currentChild)) {
            return currentChild;
        }
        Node replacedChild = nodeSupplier.apply(currentChild);
        replaceByMutableNodeWithReplacedSnapshot(snapshot, replacedChild);
        return replacedChild;
    }

    @Override
    public void removeChild(String name) {
        FileSystemLocationSnapshot snapshot = childrenMap.get(name);
        replaceByMutableNodeWithReplacedSnapshot(snapshot, null);
    }

    protected void replaceByMutableNodeWithReplacedSnapshot(@Nullable FileSystemLocationSnapshot snapshot, @Nullable Node replacement) {
        DefaultNode replacementForCurrentNode = new DefaultNode(directorySnapshot.getName(), parent);
        directorySnapshot.getChildren().forEach(childSnapshot -> {
            if (childSnapshot != snapshot) {
                replacementForCurrentNode.getOrCreateChild(
                    childSnapshot.getName(),
                    parent -> convertToNode(childSnapshot, parent)
                );
            } else if (snapshot != null && replacement != null) {
                replacementForCurrentNode.getOrCreateChild(
                    snapshot.getName(),
                    originalNode -> replacementForCurrentNode
                );
            }
        });
        parent.replaceChild(
            directorySnapshot.getName(),
            parent -> replacementForCurrentNode,
            old -> true);
    }

    private Node getChildOrMissing(String name) {
        FileSystemLocationSnapshot fileSystemLocationSnapshot = childrenMap.get(name);
        return fileSystemLocationSnapshot != null
            ? convertToNode(fileSystemLocationSnapshot, this)
            : new MissingFileNode(this, getChildAbsolutePath(name), name);
    }

    public static Node convertToNode(FileSystemLocationSnapshot snapshot, Node parent) {
        switch (snapshot.getType()) {
            case RegularFile:
                return new FileNode(parent, (RegularFileSnapshot) snapshot);
            case Directory:
                return new CompleteDirectoryNode(parent, (DirectorySnapshot) snapshot);
            case Missing:
                return new MissingFileNode(parent, snapshot.getAbsolutePath(), snapshot.getName());
            default:
                throw new AssertionError("Unknown type: " + snapshot.getType());
        }
    }

    @Override
    public String getAbsolutePath() {
        return directorySnapshot.getAbsolutePath();
    }

    @Override
    public Type getType() {
        return Type.DIRECTORY;
    }

    @Override
    public void accept(FileSystemSnapshotVisitor visitor) {
        if (visitor instanceof MerkleDirectorySnapshotBuilder) {
            ((MerkleDirectorySnapshotBuilder) visitor).resultKnown(directorySnapshot);
        } else {
            directorySnapshot.accept(visitor);
        }
    }

    @Override
    public void underLock(Runnable action) {
        parent.underLock(action);
    }
}
