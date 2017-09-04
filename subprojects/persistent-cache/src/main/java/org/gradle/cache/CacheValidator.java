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

package org.gradle.cache;

/**
 * CacheValidator interface can be used for specify a particular cache validation logic.
  */
public interface CacheValidator {
    /**
     * <p>Determines whether a cache is valid. A shared or exclusive lock is held on the cache while this method is executed, so the action is free to
     * perform read-only operations on the cache to determine its validity.
     *
     * <p>If this method returns false, then the contents of the cache are discarded and a new empty cache is created.
     *
     * @return true if value, false if not.
     */
    boolean isValid();
}
