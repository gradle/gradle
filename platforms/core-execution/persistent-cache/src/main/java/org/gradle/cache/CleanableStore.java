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

package org.gradle.cache;

import java.io.File;
import java.util.Collection;

/**
 * Represents file-based store that can be cleaned by a {@link CleanupAction}.
 */
public interface CleanableStore {

    /**
     * Returns the base directory that should be cleaned for this store.
     */
    File getBaseDir();

    /**
     * Returns the files used by this store for internal tracking
     * which should be exempt from the cleanup.
     */
    Collection<File> getReservedCacheFiles();

    String getDisplayName();
}
