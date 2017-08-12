/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.caching;

import org.gradle.api.Incubating;

import java.io.IOException;
import java.io.InputStream;

/**
 * A reader for build cache entries.
 *
 * @since 3.3
 */
@Incubating
public interface BuildCacheEntryReader {
    /**
     * Read a build cache entry from the given input stream.
     * <p>
     * The given input stream will be closed by this method.
     *
     * @param input input stream that contains the build cache entry
     * @throws IOException when an I/O error occurs when reading the cache entry from the given input stream
     */
    void readFrom(InputStream input) throws IOException;
}
