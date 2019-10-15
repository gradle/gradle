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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CompleteDirectoryNode extends AbstractSnapshotNode {
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

    @Nonnull
    @Override
    public Node getChild(ImmutableList<String> path) {
        return getChildOrMissing(path);
    }

    private Node getChildOrMissing(ImmutableList<String> path) {
        if (path.isEmpty()) {
            return this;
        }
        String childName = path.get(0);
        ImmutableList<String> remainingPath = path.subList(1, path.size());
        FileSystemLocationSnapshot fileSystemLocationSnapshot = childrenMap.get(childName);
        return fileSystemLocationSnapshot != null
            ? convertToNode(fileSystemLocationSnapshot, this).getChild(remainingPath)
            : new MissingFileNode(this, getChildAbsolutePath(childName), childName).getChild(remainingPath);
    }


    @Override
    public Node replace(ImmutableList<String> path, ChildNodeSupplier nodeSupplier, ExistingChildPredicate shouldReplaceExisting) {
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Cannot replace child with empty path");
        }
        boolean directChild = path.size() == 1;
        String childName = path.get(0);
        FileSystemLocationSnapshot snapshot = childrenMap.get(childName);
        if (snapshot == null) {
            return new MissingFileNode(this, getChildAbsolutePath(childName), childName).getChild(path.subList(1, path.size()));
        }
        Node currentChild = convertToNode(snapshot, this);
        if (directChild) {
            if (!shouldReplaceExisting.test(currentChild)) {
                return currentChild;
            }
            Node replacedChild = nodeSupplier.create(currentChild);
            replaceByMutableNodeWithReplacedSnapshot(snapshot, replacedChild);
            return replacedChild;
        }

        return currentChild.replace(path.subList(1, path.size()), nodeSupplier, shouldReplaceExisting);
    }

    @Override
    public void remove(ImmutableList<String> path) {
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Cannot remove current node");
        }

        FileSystemLocationSnapshot childSnapshot = childrenMap.get(path.get(0));
        boolean directChild = path.size() == 1;
        if (directChild || childSnapshot == null) {
            replaceByMutableNodeWithReplacedSnapshot(childSnapshot, null);
        } else {
            convertToNode(childSnapshot, this).remove(path.subList(1, path.size()));
        }
    }

    protected void replaceByMutableNodeWithReplacedSnapshot(@Nullable FileSystemLocationSnapshot snapshot, @Nullable Node replacement) {
        DefaultNode replacementForCurrentNode = new DefaultNode(directorySnapshot.getName(), parent);
        directorySnapshot.getChildren().forEach(childSnapshot -> {
            if (childSnapshot != snapshot) {
                replacementForCurrentNode.addChild(
                    childSnapshot.getName(),
                    convertToNode(childSnapshot, replacementForCurrentNode)
                );
            } else if (snapshot != null && replacement != null) {
                replacementForCurrentNode.addChild(
                    snapshot.getName(),
                    replacement
                );
            }
        });
        parent.replace(
            ImmutableList.of(directorySnapshot.getName()),
            parent -> replacementForCurrentNode,
            old -> true);
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
    public FileSystemLocationSnapshot getSnapshot() {
        return directorySnapshot;
    }

}
