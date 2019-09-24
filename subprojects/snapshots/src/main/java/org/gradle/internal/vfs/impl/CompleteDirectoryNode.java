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
import org.gradle.internal.snapshot.RegularFileSnapshot;

import java.util.Map;
import java.util.function.Function;
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
    public Node replaceChild(String name, Function<Node, Node> nodeSupplier, Function<Node, Node> replacement) {
        FileSystemLocationSnapshot snapshot = childrenMap.get(name);
        if (snapshot == null) {
            return new MissingFileNode(getChildAbsolutePath(name), name);
        }
        Node currentChild = convertToNode(snapshot, this);
        Node replacedChild = replacement.apply(currentChild);
        if (currentChild == replacedChild) {
            return currentChild;
        }

        DefaultNode replacementForCurrentNode = new DefaultNode(directorySnapshot.getName(), parent);
        directorySnapshot.getChildren().forEach(childSnapshot -> {
            if (childSnapshot != snapshot) {
                replacementForCurrentNode.getOrCreateChild(
                    childSnapshot.getName(),
                    parent -> convertToNode(childSnapshot, parent)
                );
            } else {
                if (replacedChild != null) {
                    replacementForCurrentNode.getOrCreateChild(
                        childSnapshot.getName(),
                        parent -> replacedChild
                    );
                }
            }
        });
        parent.replaceChild(
            directorySnapshot.getName(),
            parent -> { throw new AssertionError("Parent doesn't have current node as a child"); },
            originalNode -> replacementForCurrentNode
        );
        return replacedChild;
    }

    private Node getChildOrMissing(String name) {
        FileSystemLocationSnapshot fileSystemLocationSnapshot = childrenMap.get(name);
        return fileSystemLocationSnapshot != null
            ? convertToNode(fileSystemLocationSnapshot, this)
            : new MissingFileNode(getChildAbsolutePath(name), name);
    }

    private Node convertToNode(FileSystemLocationSnapshot snapshot, Node parent) {
        switch (snapshot.getType()) {
            case RegularFile:
                return new FileNode((RegularFileSnapshot) snapshot);
            case Directory:
                return new CompleteDirectoryNode(parent, (DirectorySnapshot) snapshot);
            default:
                throw new AssertionError("A complete directory cannot have missing files as children");
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
        directorySnapshot.accept(visitor);
    }

}
