/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import org.gradle.internal.classpath.CachedJarFileStore;
import org.gradle.internal.file.DefaultFileHierarchySet;
import org.gradle.internal.file.FileHierarchySet;
import org.gradle.internal.snapshot.WellKnownFileLocations;

import java.io.File;
import java.util.List;

public class DefaultWellKnownFileLocations implements WellKnownFileLocations {
    private final FileHierarchySet immutableLocations;

    public DefaultWellKnownFileLocations(List<CachedJarFileStore> fileStores) {
        FileHierarchySet immutableLocations = DefaultFileHierarchySet.of();
        for (CachedJarFileStore fileStore : fileStores) {
            for (File file : fileStore.getFileStoreRoots()) {
                immutableLocations = immutableLocations.plus(file);
            }
        }
        this.immutableLocations = immutableLocations;
    }

    @Override
    public boolean isImmutable(String path) {
        return immutableLocations.contains(path);
    }
}
