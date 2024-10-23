/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;
import java.util.List;

/**
 * Represents a location for global Gradle caches.
 *
 * The global cache is managed by Gradle, so we Gradle needs to take care
 * of informing all the infrastructure about changes to it.
 */
@ServiceScope(Scope.Global.class)
public interface GlobalCache {
    /**
     * Returns the root directories of the global cache.
     */
    List<File> getGlobalCacheRoots();
}
