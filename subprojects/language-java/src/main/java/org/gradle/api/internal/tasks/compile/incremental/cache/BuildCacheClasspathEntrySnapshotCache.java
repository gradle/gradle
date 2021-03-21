/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.cache;

import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshotData;
import org.gradle.cache.Cache;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.controller.BuildCacheLoadCommand;
import org.gradle.caching.internal.controller.BuildCacheStoreCommand;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.gradle.util.GradleVersion;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Function;

/**
 * Stores classpath entry snapshots in the build cache and loads them from there when they can't be found in the given delegate.
 */
public class BuildCacheClasspathEntrySnapshotCache implements Cache<HashCode, ClasspathEntrySnapshotData> {
    private final Cache<HashCode, ClasspathEntrySnapshotData> delegate;
    private final BuildCacheController buildCache;
    private final Serializer<ClasspathEntrySnapshotData> serializer;

    public BuildCacheClasspathEntrySnapshotCache(Cache<HashCode, ClasspathEntrySnapshotData> delegate, BuildCacheController buildCache, Serializer<ClasspathEntrySnapshotData> serializer) {
        this.delegate = delegate;
        this.buildCache = buildCache;
        this.serializer = serializer;
    }

    @Override
    public ClasspathEntrySnapshotData get(HashCode key, Function<? super HashCode, ? extends ClasspathEntrySnapshotData> factory) {
        return delegate.get(key, hash -> {
            ClasspathEntrySnapshotData data = loadFromCache(key);
            if (data == null) {
                data = factory.apply(hash);
                storeInCache(hash, data);
            }
            return data;
        });
    }

    @Nullable
    @Override
    public ClasspathEntrySnapshotData getIfPresent(HashCode key) {
        ClasspathEntrySnapshotData data = delegate.getIfPresent(key);
        if (data != null) {
            return data;
        }
        return loadFromCache(key);
    }

    private ClasspathEntrySnapshotData loadFromCache(HashCode key) {
        return buildCache.load(new DeserializingLoadCommand(key, serializer)).orElse(null);
    }

    @Override
    public void put(HashCode hash, ClasspathEntrySnapshotData data) {
        delegate.put(hash, data);
        storeInCache(hash, data);
    }

    private void storeInCache(HashCode hash, ClasspathEntrySnapshotData data) {
        buildCache.store(new SerializingStoreCommand(hash, serializer, data));
    }

    private static class DeserializingLoadCommand implements BuildCacheLoadCommand<ClasspathEntrySnapshotData> {
        private final VersionBoundCacheKey key;
        private final Serializer<ClasspathEntrySnapshotData> serializer;

        private DeserializingLoadCommand(HashCode hashCode, Serializer<ClasspathEntrySnapshotData> serializer) {
            this.key = new VersionBoundCacheKey(hashCode);
            this.serializer = serializer;
        }

        @Override
        public BuildCacheKey getKey() {
            return key;
        }

        @Override
        public Result<ClasspathEntrySnapshotData> load(InputStream inputStream) throws IOException {
            try (KryoBackedDecoder decoder = new KryoBackedDecoder(inputStream)) {
                ClasspathEntrySnapshotData result = serializer.read(decoder);
                return new Result<ClasspathEntrySnapshotData>() {
                    @Override
                    public long getArtifactEntryCount() {
                        return 1;
                    }

                    @Override
                    public ClasspathEntrySnapshotData getMetadata() {
                        return result;
                    }
                };
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    private static class SerializingStoreCommand implements BuildCacheStoreCommand {
        private final VersionBoundCacheKey key;
        private final Serializer<ClasspathEntrySnapshotData> serializer;
        private final ClasspathEntrySnapshotData data;

        public SerializingStoreCommand(HashCode hashCode, Serializer<ClasspathEntrySnapshotData> serializer, ClasspathEntrySnapshotData data) {
            this.key = new VersionBoundCacheKey(hashCode);
            this.serializer = serializer;
            this.data = data;
        }

        @Override
        public VersionBoundCacheKey getKey() {
            return key;
        }

        @Override
        public Result store(OutputStream outputStream) throws IOException {
            try (KryoBackedEncoder encoder = new KryoBackedEncoder(outputStream)){
                serializer.write(encoder, data);
                return () -> 1;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    /**
     * A {@link BuildCacheKey} that is bound to the current Gradle version, as our {@link Serializer} implementations may change over time.
     */
    private static class VersionBoundCacheKey implements BuildCacheKey {
        private final HashCode hash;

        public VersionBoundCacheKey(HashCode hash) {
            Hasher hasher = Hashing.newHasher();
            hasher.putString(GradleVersion.current().getVersion());
            hasher.putHash(hash);
            this.hash = hasher.hash();
        }

        @Override
        public String getHashCode() {
            return hash.toString();
        }

        @Override
        public byte[] toByteArray() {
            return hash.toByteArray();
        }

        @Override
        public String getDisplayName() {
            return hash.toString();
        }
    }
}
