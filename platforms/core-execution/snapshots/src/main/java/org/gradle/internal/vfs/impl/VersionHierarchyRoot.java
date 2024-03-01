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
import org.gradle.internal.snapshot.VfsRelativePath;

import javax.annotation.CheckReturnValue;

/**
 * Structure for tracking modifications in a hierarchy.
 *
 * Allows doing optimistic locking to check whether anything in a hierarchy changed.
 */
public class VersionHierarchyRoot {
    private final CaseSensitivity caseSensitivity;
    private final VersionHierarchy rootNode;

    public static VersionHierarchyRoot empty(long version, CaseSensitivity caseSensitivity) {
        return new VersionHierarchyRoot(VersionHierarchy.empty(version), caseSensitivity);
    }

    private VersionHierarchyRoot(VersionHierarchy rootNode, CaseSensitivity caseSensitivity) {
        this.caseSensitivity = caseSensitivity;
        this.rootNode = rootNode;
    }

    /**
     * The version of the sub-hierarchy at the given location.
     *
     * The version increases if there is any updated version in a parent or descendant of the location.
     * Version updates happen via {@link #updateVersion(String)}.
     */
    public long getVersion(String location) {
        VfsRelativePath relativePath = VfsRelativePath.of(location);
        return relativePath.isEmpty()
            ? rootNode.getMaxVersionInHierarchy()
            : rootNode.getVersion(relativePath, caseSensitivity);
    }

    /**
     * Creates a new hierarchy with an updated version at the location.
     */
    @CheckReturnValue
    public VersionHierarchyRoot updateVersion(String location) {
        long newVersion = rootNode.getMaxVersionInHierarchy() + 1;
        VfsRelativePath relativePath = VfsRelativePath.of(location);
        VersionHierarchy newRootNode = relativePath.isEmpty()
            ? VersionHierarchy.empty(newVersion)
            : rootNode.updateVersion(relativePath, newVersion, caseSensitivity);
        return new VersionHierarchyRoot(newRootNode, caseSensitivity);
    }
}
