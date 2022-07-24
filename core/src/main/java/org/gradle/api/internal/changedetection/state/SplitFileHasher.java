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

import org.gradle.cache.GlobalCacheLocations;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;

import java.io.File;

/**
 * A {@link FileHasher} that delegates to the global hasher for immutable files
 * and uses the local hasher for all other files. This ensures optimal cache utilization.
 */
public class SplitFileHasher implements FileHasher {
    private final FileHasher globalHasher;
    private final FileHasher localHasher;
    private final GlobalCacheLocations globalCacheLocations;

    public SplitFileHasher(FileHasher globalHasher, FileHasher localHasher, GlobalCacheLocations globalCacheLocations) {
        this.globalHasher = globalHasher;
        this.localHasher = localHasher;
        this.globalCacheLocations = globalCacheLocations;
    }

    @Override
    public HashCode hash(File file) {
        if (globalCacheLocations.isInsideGlobalCache(file.getPath())) {
            return globalHasher.hash(file);
        } else {
            return localHasher.hash(file);
        }
    }

    @Override
    public HashCode hash(File file, long length, long lastModified) {
        if (globalCacheLocations.isInsideGlobalCache(file.getPath())) {
            return globalHasher.hash(file, length, lastModified);
        } else {
            return localHasher.hash(file, length, lastModified);
        }
    }
}
