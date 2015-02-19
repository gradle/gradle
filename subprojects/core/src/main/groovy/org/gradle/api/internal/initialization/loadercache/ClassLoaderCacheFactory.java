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

import org.gradle.api.internal.changedetection.state.CachingFileSnapshotter;
import org.gradle.api.internal.hash.DefaultHasher;
import org.gradle.cache.internal.InMemoryNonExclusiveStore;
import org.gradle.internal.environment.GradleBuildEnvironment;

import java.util.HashMap;

public class ClassLoaderCacheFactory {

    private final GradleBuildEnvironment environment;
    private DefaultClassLoaderCache instance;

    public ClassLoaderCacheFactory(GradleBuildEnvironment environment) {
        this.environment = environment;
    }

    public ClassLoaderCache create() {
        if (environment.isLongLivingProcess()) {
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
            CachingFileSnapshotter fileSnapshotter = new CachingFileSnapshotter(new DefaultHasher(), new InMemoryNonExclusiveStore());
            instance = newCache(new HashClassPathSnapshotter(fileSnapshotter));
        }
    }
}
