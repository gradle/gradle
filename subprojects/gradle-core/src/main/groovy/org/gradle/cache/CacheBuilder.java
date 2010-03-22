/*
 * Copyright 2010 the original author or authors.
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

import java.util.Map;

public interface CacheBuilder {
    /**
     * Specifies the additional key properties for the cache. The cache is treated as invalid if any of the properties
     * do not match the properties used to create the cache. The default for this is an empty map.
     *
     * @param properties additional properties for the cache.
     * @return this
     */
    CacheBuilder withProperties(Map<String, ?> properties);

    /**
     * Specifies the target domain object.  This might be a task, project, or similar. The cache is scoped for the given
     * target object. The default is to use a globally-scoped cache.
     *
     * @param target The target domain object which the cache is for.
     * @return this
     */
    CacheBuilder forObject(Object target);

    /**
     * Invalidates this cache on Gradle version change. The default is to maintain a separate cache for each version.
     *
     * @return this
     */
    CacheBuilder invalidateOnVersionChange();

    /**
     * Creates the cache.
     */
    PersistentCache open();
}
