/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.cache.internal;

import org.gradle.CacheUsage;
import org.gradle.api.Action;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.*;
import org.gradle.messaging.serialize.DefaultSerializer;
import org.gradle.messaging.serialize.Serializer;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.cache.internal.FileLockManager.LockMode;

public class DefaultCacheRepository implements CacheRepository {
    private final GradleVersion version = GradleVersion.current();
    private final File globalCacheDir;
    private final CacheUsage cacheUsage;
    private final File projectCacheDir;
    private final CacheFactory factory;

    public DefaultCacheRepository(File userHomeDir, File projectCacheDir, CacheUsage cacheUsage, CacheFactory factory) {
        this.projectCacheDir = projectCacheDir;
        this.factory = factory;
        this.globalCacheDir = new File(userHomeDir, "caches");
        this.cacheUsage = cacheUsage;
    }

    public DirectoryCacheBuilder store(String key) {
        return new PersistentStoreBuilder(key);
    }

    public DirectoryCacheBuilder cache(String key) {
        return new PersistentCacheBuilder(key);
    }

    public <E> ObjectCacheBuilder<E, PersistentStateCache<E>> stateCache(Class<E> elementType, String key) {
        return new StateCacheBuilder<E>(key);
    }

    public <K, V> ObjectCacheBuilder<V, PersistentIndexedCache<K, V>> indexedCache(Class<K> keyType, Class<V> elementType, String key) {
        return new IndexedCacheBuilder<K, V>(key);
    }

    private abstract class AbstractCacheBuilder<T> implements CacheBuilder<T> {
        private final String key;
        private Map<String, ?> properties = Collections.emptyMap();
        private Object target;
        private VersionStrategy versionStrategy = VersionStrategy.CachePerVersion;
        private CacheValidator validator;

        protected AbstractCacheBuilder(String key) {
            this.key = key;
        }

        public CacheBuilder<T> withProperties(Map<String, ?> properties) {
            this.properties = properties;
            return this;
        }

        public CacheBuilder<T> withVersionStrategy(VersionStrategy strategy) {
            this.versionStrategy = strategy;
            return this;
        }

        public CacheBuilder<T> forObject(Object target) {
            this.target = target;
            return this;
        }

        public CacheBuilder<T> withValidator(CacheValidator validator) {
            this.validator = validator;
            return this;
        }

        public T open() {
            File cacheBaseDir;
            Map<String, Object> properties = new HashMap<String, Object>(this.properties);
            if (target == null) {
                cacheBaseDir = globalCacheDir;
            } else if (target instanceof Gradle) {
                Gradle gradle = (Gradle) target;
                File rootProjectDir = gradle.getRootProject().getProjectDir();
                cacheBaseDir = maybeProjectCacheDir(rootProjectDir);
            } else if (target instanceof File) {
                cacheBaseDir = new File((File) target, ".gradle");
            } else {
                throw new IllegalArgumentException(String.format("Cannot create cache for unrecognised domain object %s.", target));
            }
            switch (versionStrategy) {
                case SharedCache:
                    // Use the root directory
                    break;
                case CachePerVersion:
                    cacheBaseDir = new File(cacheBaseDir, version.getVersion());
                    break;
                case SharedCacheInvalidateOnVersionChange:
                    // Include the 'noVersion' suffix for backwards compatibility
                    cacheBaseDir = new File(cacheBaseDir, "noVersion");
                    properties.put("gradle.version", version.getVersion());
                    break;
            }
            return doOpen(new File(cacheBaseDir, key), properties, validator);
        }

        protected abstract T doOpen(File cacheDir, Map<String, ?> properties, CacheValidator validator);

        private File maybeProjectCacheDir(File potentialParentDir) {
            if (projectCacheDir != null) {
                return projectCacheDir;
            }
            return new File(potentialParentDir, ".gradle");
        }
    }

    private class PersistentCacheBuilder extends AbstractCacheBuilder<PersistentCache> implements DirectoryCacheBuilder {
        Action<? super PersistentCache> initializer;
        LockMode lockMode = LockMode.Shared;
        String displayName;

        protected PersistentCacheBuilder(String key) {
            super(key);
        }

        @Override
        public DirectoryCacheBuilder forObject(Object target) {
            super.forObject(target);
            return this;
        }

        @Override
        public DirectoryCacheBuilder withProperties(Map<String, ?> properties) {
            super.withProperties(properties);
            return this;
        }

        @Override
        public DirectoryCacheBuilder withVersionStrategy(VersionStrategy strategy) {
            super.withVersionStrategy(strategy);
            return this;
        }

        @Override
        public DirectoryCacheBuilder withValidator(CacheValidator validator) {
            super.withValidator(validator);
            return this;
        }

        public DirectoryCacheBuilder withInitializer(Action<? super PersistentCache> initializer) {
            this.initializer = initializer;
            return this;
        }

        public DirectoryCacheBuilder withDisplayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public DirectoryCacheBuilder withLockMode(LockMode lockMode) {
            this.lockMode = lockMode;
            return this;
        }

        @Override
        protected PersistentCache doOpen(File cacheDir, Map<String, ?> properties, CacheValidator validator) {
            return factory.open(cacheDir, displayName, cacheUsage, validator, properties, lockMode, initializer);
        }
    }

    private class PersistentStoreBuilder extends PersistentCacheBuilder {
        private PersistentStoreBuilder(String key) {
            super(key);
        }

        @Override
        protected PersistentCache doOpen(File cacheDir, Map<String, ?> properties, CacheValidator validator) {
            if (!properties.isEmpty()) {
                throw new UnsupportedOperationException("Properties are not supported for stores.");
            }
            return factory.openStore(cacheDir, displayName, lockMode, initializer);
        }
    }

    private abstract class AbstractObjectCacheBuilder<E, T> extends AbstractCacheBuilder<T> implements ObjectCacheBuilder<E, T> {
        protected Serializer<E> serializer = new DefaultSerializer<E>();

        protected AbstractObjectCacheBuilder(String key) {
            super(key);
        }

        @Override
        public ObjectCacheBuilder<E, T> forObject(Object target) {
            super.forObject(target);
            return this;
        }

        @Override
        public ObjectCacheBuilder<E, T> withProperties(Map<String, ?> properties) {
            super.withProperties(properties);
            return this;
        }

        @Override
        public ObjectCacheBuilder<E, T> withVersionStrategy(VersionStrategy strategy) {
            super.withVersionStrategy(strategy);
            return this;
        }

        public ObjectCacheBuilder<E, T> withSerializer(Serializer<E> serializer) {
            this.serializer = serializer;
            return this;
        }
    }

    private class StateCacheBuilder<E> extends AbstractObjectCacheBuilder<E, PersistentStateCache<E>>  {
        protected StateCacheBuilder(String key) {
            super(key);
        }

        @Override
        protected PersistentStateCache<E> doOpen(File cacheDir, Map<String, ?> properties, CacheValidator validator) {
            return factory.openStateCache(cacheDir, cacheUsage, validator, properties, LockMode.Exclusive, serializer);
        }
    }

    private class IndexedCacheBuilder<K, V> extends AbstractObjectCacheBuilder<V, PersistentIndexedCache<K, V>> {
        private IndexedCacheBuilder(String key) {
            super(key);
        }

        @Override
        protected PersistentIndexedCache<K, V> doOpen(File cacheDir, Map<String, ?> properties, CacheValidator validator) {
            return factory.openIndexedCache(cacheDir, cacheUsage, validator, properties, LockMode.Exclusive, serializer);
        }
    }
}
