/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.classpath;

import org.gradle.cache.GlobalCacheLocations;
import org.gradle.internal.file.FileType;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.util.internal.GFileUtils;

import java.io.File;

public class CopyingClasspathFileTransformer implements ClasspathFileTransformer {
    private final GlobalCacheLocations globalCacheLocations;

    public CopyingClasspathFileTransformer(GlobalCacheLocations globalCacheLocations) {
        this.globalCacheLocations = globalCacheLocations;
    }

    @Override
    public File transform(File source, FileSystemLocationSnapshot sourceSnapshot, File cacheDir) {
        // Copy files into the cache, if it is possible that loading the file in a ClassLoader may cause locking problems if the file is deleted

        if (sourceSnapshot.getType() != FileType.RegularFile) {
            // Directories are ok to use outside the cache
            return source;
        }
        if (globalCacheLocations.isInsideGlobalCache(source.getAbsolutePath())) {
            // The global caches are additive only, so we can use it directly since it shouldn't be deleted or changed during the build.
            return source;
        }

        // Copy the file into the cache
        File cachedFile = new File(cacheDir, "o_" + sourceSnapshot.getHash().toString() + '/' + source.getName());
        if (!cachedFile.isFile()) {
            // Just copy the jar
            GFileUtils.copyFile(source, cachedFile);
        }
        return cachedFile;
    }
}
