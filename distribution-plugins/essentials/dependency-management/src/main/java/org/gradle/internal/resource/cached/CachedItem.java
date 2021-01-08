/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.resource.cached;

import javax.annotation.Nullable;
import java.io.File;

public interface CachedItem {
    /**
     * True if this cache entry represents that the resource does not exist.
     *
     * For a missing resource, all of the values will be null or similar “non” values.
     *
     * @return Whether this is a “missing” entry or not.
     */
    boolean isMissing();

    /**
     * The cached version of the external resource as a local file.
     *
     * @return The cached version of the external resource as a local file, or null if this {@link #isMissing()}.
     */
    @Nullable
    File getCachedFile();

    /**
     * The timestamp of when this cache entry was created.
     *
     * @return The timestamp of when this cache entry was created.
     */
    long getCachedAt();


}
