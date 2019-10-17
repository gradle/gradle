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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class NodeWithChildren extends AbstractNode {
    private final List<Node> children;

    public NodeWithChildren(String prefix, List<Node> children) {
        super(prefix);
        assert !children.isEmpty();
        this.children = children;
    }

    @Override
    public Optional<Node> invalidate(String path) {
        int maxPos = Math.min(getPrefix().length(), path.length());
        int prefixLen = sizeOfCommonPrefix(getPrefix(), path, 0);
        if (prefixLen == maxPos) {
            if (getPrefix().length() >= path.length()) {
                return Optional.empty();
            }
            int startNextSegment = getPrefix().length() + 1;
            List<Node> merged = new ArrayList<>(children.size() + 1);
            boolean matched = false;
            for (Node child : children) {
                if (!matched && sizeOfCommonPrefix(child.getPrefix(), path, startNextSegment) > 0) {
                    // TODO - we've already calculated the common prefix and calling plus() will calculate it again
                    child.invalidate(path.substring(startNextSegment)).ifPresent(merged::add);
                    matched = true;
                } else {
                    merged.add(child);
                }
            }
            if (!matched) {
                return Optional.of(this);
            }
            return merged.isEmpty() ? Optional.empty() : Optional.of(new NodeWithChildren(getPrefix(), merged));

        }
        return Optional.of(this);
    }

    @Override
    public Node update(String path, FileSystemLocationSnapshot snapshot) {
        int maxPos = Math.min(getPrefix().length(), path.length());
        int prefixLen = sizeOfCommonPrefix(getPrefix(), path, 0);
        if (prefixLen == maxPos) {
            if (getPrefix().length() == path.length()) {
                // Path == prefix
                return new SnapshotNode(path, snapshot);
            }
            if (getPrefix().length() < path.length()) {
                // Path is a descendant of this
                int startNextSegment = getPrefix().length() + 1;
                List<Node> merged = new ArrayList<>(children.size() + 1);
                boolean matched = false;
                for (Node child : children) {
                    if (!matched && sizeOfCommonPrefix(child.getPrefix(), path, startNextSegment) > 0) {
                        // TODO - we've already calculated the common prefix and calling plus() will calculate it again
                        merged.add(child.update(path.substring(startNextSegment), snapshot));
                        matched = true;
                    } else {
                        merged.add(child);
                    }
                }
                if (!matched) {
                    merged.add(new SnapshotNode(path.substring(startNextSegment), snapshot));
                }
                return new NodeWithChildren(getPrefix(), merged);
            } else {
                // Path is an ancestor of this
                return new SnapshotNode(path, snapshot);
            }
        }
        String commonPrefix = getPrefix().substring(0, prefixLen);
        Node newThis = new NodeWithChildren(getPrefix().substring(prefixLen + 1), children);
        Node sibling = new SnapshotNode(path.substring(prefixLen + 1), snapshot);
        return new NodeWithChildren(commonPrefix, ImmutableList.of(newThis, sibling));
    }

    @Override
    public Optional<FileSystemLocationSnapshot> getSnapshot(String filePath, int offset) {
        if (!isChildOfOrThis(filePath, offset, getPrefix())) {
            return Optional.empty();
        }
        int startNextSegment = offset + getPrefix().length() + 1;
        for (Node child : children) {
            Optional<FileSystemLocationSnapshot> childSnapshot = child.getSnapshot(filePath, startNextSegment);
            if (childSnapshot.isPresent()) {
                return childSnapshot;
            }
        }
        return Optional.empty();
    }

    @Override
    public void collect(int depth, List<String> prefixes) {
        if (depth == 0) {
            prefixes.add(getPrefix());
        } else {
            prefixes.add(depth + ":" + getPrefix().replace(File.separatorChar, '/'));
        }
        for (Node child : children) {
            child.collect(depth + 1, prefixes);
        }
    }
}
