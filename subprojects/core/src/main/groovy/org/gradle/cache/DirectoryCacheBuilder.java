/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.cache.internal.FileLockManager;

import java.util.Map;

public interface DirectoryCacheBuilder extends CacheBuilder<PersistentCache> {
    DirectoryCacheBuilder withVersionStrategy(VersionStrategy strategy);

    DirectoryCacheBuilder withProperties(Map<String, ?> properties);

    DirectoryCacheBuilder forObject(Object target);

    /**
     * Specifies the display name for this cache. This display name is used in logging and error messages.
     */
    DirectoryCacheBuilder withDisplayName(String displayName);

    /**
     * Specifies the <em>initial</em> lock mode to use. See {@link PersistentCache} for details.
     *
     * <p>Note that not every combination of cache type and lock mode is supported.
     */
    DirectoryCacheBuilder withLockMode(FileLockManager.LockMode lockMode);

    /**
     * Specifies an action to execute to initialize the cache contents, if the cache does not exist or is invalid. An exclusive lock is held while the initializer is executing, to prevent
     * cross-process access.
     */
    DirectoryCacheBuilder withInitializer(Action<? super PersistentCache> initializer);

    DirectoryCacheBuilder withValidator(CacheValidator validator);
}
