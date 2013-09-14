/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import org.gradle.StartParameter;
import org.gradle.cache.internal.MultiProcessSafePersistentIndexedCache;
import org.gradle.internal.Factory;
import org.gradle.internal.GradleBuildEnvironment;

import java.io.File;

public class InMemoryPersistentCacheDecoratorFactory implements Factory<InMemoryPersistentCacheDecorator> {
    private InMemoryPersistentCacheDecorator cache;
    private StartParameter startParameter;

    public InMemoryPersistentCacheDecoratorFactory(InMemoryPersistentCacheDecorator cache, StartParameter startParameter) {
        this.cache = cache;
        this.startParameter = startParameter;
    }

    public InMemoryPersistentCacheDecorator create() {
        if (startParameter instanceof GradleBuildEnvironment && ((GradleBuildEnvironment) startParameter).isLongLivingProcess()) {
            return cache;
        } else {
            return new NoOpDecorator();
        }
    }

    private static class NoOpDecorator implements InMemoryPersistentCacheDecorator {
        public <K, V> MultiProcessSafePersistentIndexedCache<K, V> withMemoryCaching(File cacheFile, MultiProcessSafePersistentIndexedCache<K, V> original) {
            return original;
        }
    }
}