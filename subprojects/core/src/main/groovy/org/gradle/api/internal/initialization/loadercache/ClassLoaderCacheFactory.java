/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.initialization.loadercache;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.util.HashMap;

public class ClassLoaderCacheFactory {

    public final static String TOGGLE_CACHING_PROPERTY = "org.gradle.caching.classloaders";
    private final static Logger LOGGER = Logging.getLogger(ClassLoaderCacheFactory.class);
    private DefaultClassLoaderCache instance;

    public ClassLoaderCache create() {
        if ("true".equalsIgnoreCase(System.getProperty(TOGGLE_CACHING_PROPERTY))) {
            maybeInit();
            return instance;
        }
        return newCache(new FileClassPathSnapshotter());
    }

    private DefaultClassLoaderCache newCache(ClassPathSnapshotter snapshotter) {
        return new DefaultClassLoaderCache(new HashMap<DefaultClassLoaderCache.Key, ClassLoader>(), snapshotter);
    }

    private void maybeInit() {
        if (instance == null) {
            instance = newCache(new HashClassPathSnapshotter());
            LOGGER.lifecycle("Initialized global ClassLoader cache.");
        }
    }
}
