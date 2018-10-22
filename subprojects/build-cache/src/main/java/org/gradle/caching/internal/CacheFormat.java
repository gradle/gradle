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

package org.gradle.caching.internal;

public interface CacheFormat {
    // Initial format version
    // NOTE: This should be changed whenever we change the way we pack a cache entry, such as
    // - changing from gzip to bzip2.
    // - adding/removing properties to the origin metadata
    // - using a different format for the origin metadata
    // - any major changes of the layout of a cache entry
    int CACHE_ENTRY_FORMAT = 1;
}
