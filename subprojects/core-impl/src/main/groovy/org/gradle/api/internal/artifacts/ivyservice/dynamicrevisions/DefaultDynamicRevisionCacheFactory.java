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
package org.gradle.api.internal.artifacts.ivyservice.dynamicrevisions;

import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.util.TimeProvider;
import org.gradle.util.TrueTimeProvider;

import java.io.File;

public class DefaultDynamicRevisionCacheFactory implements DynamicRevisionCacheFactory {
    private final TimeProvider timeProvider = new TrueTimeProvider();
    private final CacheLockingManager cacheLockingManager;

    public DefaultDynamicRevisionCacheFactory(CacheLockingManager cacheLockingManager) {
        this.cacheLockingManager = cacheLockingManager;
    }

    public DynamicRevisionCache create(File cacheDir) {
        return new SingleFileBackedDynamicRevisionCache(timeProvider, cacheDir, cacheLockingManager);
    }
}
