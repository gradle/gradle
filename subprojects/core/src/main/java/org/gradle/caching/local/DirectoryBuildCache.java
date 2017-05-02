/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.local;

import org.gradle.api.Incubating;
import org.gradle.api.Nullable;
import org.gradle.caching.configuration.AbstractBuildCache;

/**
 * Configuration object for the local directory build cache.
 *
 * @since 3.5
 */
@Incubating
public class DirectoryBuildCache extends AbstractBuildCache {
    private final static long DEFAULT_MAXIMUM_CACHE_SIZE = 5*1024; // 5 GB

    private Object directory;
    private long targetSizeInMB = DEFAULT_MAXIMUM_CACHE_SIZE; // 5GB

    /**
     * Returns the directory to use to store the build cache.
     */
    @Nullable
    public Object getDirectory() {
        return directory;
    }

    /**
     * Sets the directory to use to store the build cache.
     *
     * The directory is evaluated as per {@link org.gradle.api.Project#file(Object)}.
     */
    public void setDirectory(Object directory) {
        this.directory = directory;
    }

    /**
     * Returns the target maximum size of the build cache.
     *
     * @return The target maximum size of the build cache in megabytes.
     *
     * @since 4.0
     */
    public long getTargetSizeInMB() {
        return targetSizeInMB;
    }

    /**
     * Defaults to {@value DEFAULT_MAXIMUM_CACHE_SIZE} MB.
     *
     * <p>
     * Must be at least 1 MB in size.
     *
     * @param targetSizeInMB the new target maximum size of the build cache.
     *
     * @since 4.0
     */
    public void setTargetSizeInMB(long targetSizeInMB) {
        if (targetSizeInMB < 1) {
            throw new IllegalArgumentException("Must have at least 1 MB of space allowed for the directory cache.");
        }
        this.targetSizeInMB = targetSizeInMB;
    }
}
