/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.jvm.inspection;

import org.gradle.api.NonNullApi;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.IndexedCacheParameters;
import org.gradle.cache.PersistentCache;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.jvm.toolchain.internal.InstallationLocation;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * A {@link JvmMetadataDetector} that caches the results of the JVM installation metadata in a persistent cache.
 *
 * @implNote This currently only persistently caches the results for JVMs that are auto-provisioned.
 */
@NonNullApi
public class PersistentJvmMetadataDetector implements JvmMetadataDetector, Closeable {
    private final JvmMetadataDetector delegate;
    private final PersistentCache cache;
    private final IndexedCache<File, JvmInstallationMetadata> indexedCache;

    public PersistentJvmMetadataDetector(JvmMetadataDetector delegate, CacheBuilder cacheBuilder) {
        this.delegate = delegate;
        this.cache = cacheBuilder.withInitialLockMode(FileLockManager.LockMode.None).open();
        // TODO: This cache should be cleaned up
        IndexedCacheParameters<File, JvmInstallationMetadata> parameters = IndexedCacheParameters.of(
            "metadata",
            new FileSerializer(),
            new JvmInstallationMetadataSerializer()
        );
        this.indexedCache = cache.createIndexedCache(parameters);
    }

    @Override
    public JvmInstallationMetadata getMetadata(InstallationLocation javaInstallationLocation) {
        // If the Java installation was auto-provisioned, we can trust that it will not change
        if (javaInstallationLocation.isAutoProvisioned()) {
            return cache.useCache(() -> indexedCache.get(javaInstallationLocation.getLocation(), key -> delegate.getMetadata(javaInstallationLocation)));
        } else {
            // Otherwise, we need to reprobe each time
            return delegate.getMetadata(javaInstallationLocation);
        }
    }

    @Override
    public void close() throws IOException {
        cache.close();
    }

    @NonNullApi
    private static class FileSerializer implements Serializer<File> {
        @Override
        public File read(Decoder decoder) throws Exception {
            return new File(decoder.readString());
        }

        @Override
        public void write(Encoder encoder, File value) throws Exception {
            encoder.writeString(value.getAbsolutePath());
        }
    }

    @NonNullApi
    private static class JvmInstallationMetadataSerializer implements Serializer<JvmInstallationMetadata> {
        @Override
        public JvmInstallationMetadata read(Decoder decoder) throws Exception {
            return JvmInstallationMetadata.from(
                new File(decoder.readString()),
                decoder.readString(),
                decoder.readString(),
                decoder.readString(),
                decoder.readString(),
                decoder.readString(),
                decoder.readString(),
                decoder.readString(),
                decoder.readString());
        }

        @Override
        public void write(Encoder encoder, JvmInstallationMetadata value) throws Exception {
            encoder.writeString(value.getJavaHome().toString());
            encoder.writeString(value.getJavaVersion());
            encoder.writeString(value.getVendor().getRawVendor());
            encoder.writeString(value.getRuntimeName());
            encoder.writeString(value.getRuntimeVersion());
            encoder.writeString(value.getJvmName());
            encoder.writeString(value.getJvmVersion());
            encoder.writeString(value.getJvmVendor());
            encoder.writeString(value.getArchitecture());
        }
    }
}
