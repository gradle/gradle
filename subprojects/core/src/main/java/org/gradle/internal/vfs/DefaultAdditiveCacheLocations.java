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

package org.gradle.internal.vfs;

import org.gradle.internal.file.DefaultFileHierarchySet;
import org.gradle.internal.file.FileHierarchySet;

import java.io.File;
import java.util.List;

public class DefaultAdditiveCacheLocations implements AdditiveCacheLocations {
    private final FileHierarchySet additiveCacheRoots;

    public DefaultAdditiveCacheLocations(List<AdditiveCache> fileStores) {
        FileHierarchySet additiveCacheRoots = DefaultFileHierarchySet.of();
        for (AdditiveCache fileStore : fileStores) {
            for (File file : fileStore.getAdditiveCacheRoots()) {
                additiveCacheRoots = additiveCacheRoots.plus(file);
            }
        }
        this.additiveCacheRoots = additiveCacheRoots;
    }

    @Override
    public boolean isInsideAdditiveCache(String path) {
        return additiveCacheRoots.contains(path);
    }
}
