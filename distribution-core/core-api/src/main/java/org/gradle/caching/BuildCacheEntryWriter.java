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

import java.io.IOException;
import java.io.OutputStream;

/**
 * Writer to serialize a build cache entry.
 *
 * @since 3.3
 */
public interface BuildCacheEntryWriter {
    /**
     * Writes a build cache entry to the given stream.
     * <p>
     * The given output stream will be closed by this method.
     *
     * @param output output stream to write build cache entry to
     * @throws IOException when an I/O error occurs when writing the cache entry to the given output stream
     */
    void writeTo(OutputStream output) throws IOException;

    /**
     * Returns the size of the build cache entry to be written.
     *
     * @return the size of the build cache entry to be written.
     * @since 4.1
     */
    long getSize();
}
