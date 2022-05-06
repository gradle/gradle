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

    public long getVersionFor(String location) {
        VfsRelativePath relativePath = VfsRelativePath.of(location);
        return relativePath.isEmpty()
            ? rootNode.getVersion()
            : rootNode.getVersionFor(relativePath, caseSensitivity);
    }

    @CheckReturnValue
    public VersionHierarchyRoot increaseVersion(String path, long newVersion) {
        VfsRelativePath relativePath = VfsRelativePath.of(path);
        VersionHierarchy newRootNode = relativePath.isEmpty()
            ? VersionHierarchy.empty(newVersion)
            : rootNode.increaseVersion(relativePath, newVersion, caseSensitivity);
        return new VersionHierarchyRoot(newRootNode, caseSensitivity);
    }
}
