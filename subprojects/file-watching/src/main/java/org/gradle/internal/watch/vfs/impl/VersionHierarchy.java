/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.watch.vfs.impl;

import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.snapshot.ChildMap;
import org.gradle.internal.snapshot.EmptyChildMap;
import org.gradle.internal.snapshot.VfsRelativePath;

public class VersionHierarchy {
    private final long version;
    private final CaseSensitivity caseSensitivity;
    private final ChildMap<VersionHierarchy> children;

    public static VersionHierarchy empty(long rootVersion, CaseSensitivity caseSensitivity) {
        return new VersionHierarchy(EmptyChildMap.getInstance(), rootVersion, caseSensitivity);
    }

    public VersionHierarchy(ChildMap<VersionHierarchy> children, long version, CaseSensitivity caseSensitivity) {
        this.children = children;
        this.version = version;
        this.caseSensitivity = caseSensitivity;
    }

    public long getVersionFor(String location) {
        VfsRelativePath relativePath = VfsRelativePath.of(location);
        return relativePath.isEmpty()
            ? getVersion()
            : getVersionFor(relativePath);
    }

    public long getVersionFor(VfsRelativePath relativePath) {
        return children.withNode(relativePath, caseSensitivity, new ChildMap.NodeHandler<VersionHierarchy, Long>() {
            @Override
            public Long handleAsDescendantOfChild(VfsRelativePath pathInChild, VersionHierarchy child) {
                return child.getVersionFor(pathInChild);
            }

            @Override
            public Long handleAsAncestorOfChild(String childPath, VersionHierarchy child) {
                return child.getVersion();
            }

            @Override
            public Long handleExactMatchWithChild(VersionHierarchy child) {
                return child.getVersion();
            }

            @Override
            public Long handleUnrelatedToAnyChild() {
                return getVersion();
            }
        });
    }

    public VersionHierarchy increaseVersion(String path, long newVersion) {
        VfsRelativePath relativePath = VfsRelativePath.of(path);
        return relativePath.isEmpty()
            ? empty(newVersion, caseSensitivity)
            : increaseVersion(relativePath, newVersion);
    }

    public VersionHierarchy increaseVersion(VfsRelativePath relativePath, long newVersion) {
        ChildMap<VersionHierarchy> newChildren = children.store(relativePath, caseSensitivity, new ChildMap.StoreHandler<VersionHierarchy>() {
            @Override
            public VersionHierarchy handleAsDescendantOfChild(VfsRelativePath pathInChild, VersionHierarchy child) {
                return child.increaseVersion(pathInChild, newVersion);
            }

            @Override
            public VersionHierarchy handleAsAncestorOfChild(String childPath, VersionHierarchy child) {
                return createChild();
            }

            @Override
            public VersionHierarchy mergeWithExisting(VersionHierarchy child) {
                return createChild();
            }

            @Override
            public VersionHierarchy createChild() {
                return new VersionHierarchy(EmptyChildMap.getInstance(), newVersion, caseSensitivity);
            }

            @Override
            public VersionHierarchy createNodeFromChildren(ChildMap<VersionHierarchy> children) {
                return createChild();
            }
        });
        return new VersionHierarchy(newChildren, newVersion, caseSensitivity);
    }

    public long getVersion() {
        return version;
    }
}
