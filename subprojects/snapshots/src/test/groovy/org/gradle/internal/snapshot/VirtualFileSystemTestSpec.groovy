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

package org.gradle.internal.snapshot

import javax.annotation.Nullable

class VirtualFileSystemTestSpec {
    private static final String ABSOLUTE_PATH_PREFIX = "/home/user/project/my/directory"

    final List<String> childPaths
    final VfsRelativePath searchedPath
    final String selectedChildPath

    VirtualFileSystemTestSpec(List<String> childPaths, String relativeSearchedPath, @Nullable String selectedChildPath) {
        this.childPaths = childPaths
        this.searchedPath = VfsRelativePath.of("${ABSOLUTE_PATH_PREFIX}/${relativeSearchedPath}").pathFromChild(VfsRelativePath.of(ABSOLUTE_PATH_PREFIX).asString)
        this.selectedChildPath = selectedChildPath
    }

    @Override
    String toString() {
        return "${searchedPath.asString} selects ${selectedChildPath ?: "no child"} of ${childPaths}"
    }
}
