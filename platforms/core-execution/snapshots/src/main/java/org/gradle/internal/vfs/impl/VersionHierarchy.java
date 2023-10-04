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

package org.gradle.internal.vfs.impl;

import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.snapshot.ChildMap;
import org.gradle.internal.snapshot.EmptyChildMap;
import org.gradle.internal.snapshot.VfsRelativePath;

import javax.annotation.CheckReturnValue;

/**
 * Node in a structure for tracking modifications in a hierarchy.
 *
 * Allows doing optimistic locking to check whether anything in a hierarchy changed.
 */
public class VersionHierarchy {
    private final long version;
    // We store the maximum version as a performance optimization, so we don't need
    // to query the children every time we query the version for a hierarchy.
    private final long maxVersionInHierarchy;
    private final ChildMap<VersionHierarchy> children;

    public static VersionHierarchy empty(long version) {
        return new VersionHierarchy(EmptyChildMap.getInstance(), version, version);
    }

    private VersionHierarchy(ChildMap<VersionHierarchy> children, long version, long maxVersionInHierarchy) {
        this.children = children;
        this.version = version;
        this.maxVersionInHierarchy = maxVersionInHierarchy;
    }

    /**
     * Queries the version for the hierarchy at relative path.
     *
     * The version for the hierarchy is the maximum version of all the locations in the hierarchy.
     */
    public long getVersion(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        return children.withNode(relativePath, caseSensitivity, new ChildMap.NodeHandler<VersionHierarchy, Long>() {
            @Override
            public Long handleAsDescendantOfChild(VfsRelativePath pathInChild, VersionHierarchy child) {
                return child.getVersion(pathInChild, caseSensitivity);
            }

            @Override
            public Long handleAsAncestorOfChild(String childPath, VersionHierarchy child) {
                return child.getMaxVersionInHierarchy();
            }

            @Override
            public Long handleExactMatchWithChild(VersionHierarchy child) {
                return child.getMaxVersionInHierarchy();
            }

            @Override
            public Long handleUnrelatedToAnyChild() {
                return version;
            }
        });
    }

    /**
     * Creates a new {@link VersionHierarchy} from this {@link VersionHierarchy} with `newVersion` at `relativePath`.
     *
     * This method keeps the invariant that the version of a child is bigger than the version of the parent.
     * This is because modifying the parent implies that all of its children have been modified, so they need to have at least the same version.
     *
     * @param newVersion The new version. Must be bigger than the current maximum version in this hierarchy.
     */
    @CheckReturnValue
    public VersionHierarchy updateVersion(VfsRelativePath relativePath, long newVersion, CaseSensitivity caseSensitivity) {
        ChildMap<VersionHierarchy> newChildren = children.store(relativePath, caseSensitivity, new ChildMap.StoreHandler<VersionHierarchy>() {
            @Override
            public VersionHierarchy handleAsDescendantOfChild(VfsRelativePath pathInChild, VersionHierarchy child) {
                return child.updateVersion(pathInChild, newVersion, caseSensitivity);
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
                return VersionHierarchy.empty(newVersion);
            }

            @Override
            public VersionHierarchy createNodeFromChildren(ChildMap<VersionHierarchy> children) {
                return new VersionHierarchy(children, version, newVersion);
            }
        });
        return new VersionHierarchy(newChildren, version, newVersion);
    }

    public long getMaxVersionInHierarchy() {
        return maxVersionInHierarchy;
    }
}
