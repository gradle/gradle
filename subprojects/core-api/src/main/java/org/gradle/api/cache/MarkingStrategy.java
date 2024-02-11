/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.cache;

import org.gradle.api.Incubating;
import org.gradle.api.internal.cache.CacheDirTagMarkingStrategy;
import org.gradle.api.internal.cache.NoMarkingStrategy;

import java.io.File;

/**
 * Represents a method of marking a cache directory. This is used to mark Gradle's cache directories.
 *
 * <p>
 * You may implement your own marking strategy by implementing this interface and setting {@link CacheConfigurations#getMarkingStrategy()}.
 * </p>
 *
 * @since 8.1
 */
@Incubating
public interface MarkingStrategy {
    /**
     * Marking strategy that marks the cache directory with a {@code CACHEDIR.TAG} file.
     *
     * @see <a href="https://bford.info/cachedir/">Cache Directory Tagging Specification</a>
     */
    MarkingStrategy CACHEDIR_TAG = new CacheDirTagMarkingStrategy();

    /**
     * Marking strategy that does not mark the cache directory.
     */
    MarkingStrategy NONE = new NoMarkingStrategy();

    /**
     * Try to mark the given cache directory. If an I/O error occurs, this method should not throw an error,
     * but instead log the error at an appropriate level, and return.
     *
     * @param file the cache directory to mark
     */
    void tryMarkCacheDirectory(File file);
}
