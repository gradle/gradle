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

package org.gradle.caching.local.internal;

import org.gradle.api.Action;
import org.gradle.caching.BuildCacheKey;

import java.io.File;

public interface BuildCacheTempFileStore {

    String PARTIAL_FILE_SUFFIX = ".part";

    /**
     * Run the given action with a temp file allocated based on the given cache key.
     * The temp file will be deleted once the action is completed.
     */
    void withTempFile(BuildCacheKey key, Action<? super File> action);

}
