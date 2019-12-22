/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.verification.signatures;

import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.cache.internal.filelock.LockOptionsBuilder;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.security.internal.PublicKeyService;
import org.gradle.util.BuildCommencedTimeProvider;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.gradle.security.internal.SecuritySupport.toHexString;

public class CrossBuildCachingKeyService implements PublicKeyService, Closeable {
    private final static long MISSING_KEY_TIMEOUT = TimeUnit.MILLISECONDS.convert(24, TimeUnit.HOURS);

    private final PersistentCache cache;
    private final BuildOperationExecutor buildOperationExecutor;
    private final PublicKeyService delegate;
    private final BuildCommencedTimeProvider timeProvider;
    private final boolean refreshKeys;
    private final PersistentIndexedCache<Long, CacheEntry<PGPPublicKey>> publicKeys;
    private final PersistentIndexedCache<Long, CacheEntry<PGPPublicKeyRing>> publicKeyRings;
    private final ProducerGuard<Long> guard = ProducerGuard.adaptive();

    public CrossBuildCachingKeyService(CacheRepository cacheRepository,
                                       InMemoryCacheDecoratorFactory decoratorFactory,
                                       BuildOperationExecutor buildOperationExecutor,
                                       PublicKeyService delegate,
                                       BuildCommencedTimeProvider timeProvider,
                                       boolean refreshKeys) {
        cache = cacheRepository
            .cache("signatures")
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
            .withLockOptions(LockOptionsBuilder.mode(FileLockManager.LockMode.None))
            .open();
        this.buildOperationExecutor = buildOperationExecutor;
        this.delegate = delegate;
        this.timeProvider = timeProvider;
        this.refreshKeys = refreshKeys;
        PersistentIndexedCacheParameters<Long, CacheEntry<PGPPublicKey>> publicKeyParams = PersistentIndexedCacheParameters.of(
            "publickeys",
            BaseSerializerFactory.LONG_SERIALIZER,
            new PublicKeyEntrySerializer()
        ).withCacheDecorator(
            decoratorFactory.decorator(2000, true)
        );
        publicKeys = cache.createCache(publicKeyParams);

        PersistentIndexedCacheParameters<Long, CacheEntry<PGPPublicKeyRing>> keyringParams = PersistentIndexedCacheParameters.of(
            "keyrings",
            BaseSerializerFactory.LONG_SERIALIZER,
            new PublicKeyRingSerializer()
        ).withCacheDecorator(
            decoratorFactory.decorator(2000, true)
        );
        publicKeyRings = cache.createCache(keyringParams);
    }

    @Override
    public void close() {
        cache.close();
    }

    private boolean hasExpired(CacheEntry<?> key) {
        if (key.value != null) {
            // if a key was found in the cache, it's permanent
            return false;
        }
        long elapsed = key.timestamp - timeProvider.getCurrentTime();
        return refreshKeys || elapsed > MISSING_KEY_TIMEOUT;
    }

    @Override
    public Optional<PGPPublicKey> findPublicKey(long id) {
        CacheEntry<PGPPublicKey> cacheEntry = publicKeys.get(id);
        if (cacheEntry == null || hasExpired(cacheEntry)) {
            long currentTime = timeProvider.getCurrentTime();
            Optional<PGPPublicKeyRing> keyRing = findKeyRing(id);
            if (keyRing.isPresent()) {
                Iterator<PGPPublicKey> it = keyRing.get().getPublicKeys();
                while (it.hasNext()) {
                    PGPPublicKey key = it.next();
                    if (key.getKeyID() == id) {
                        publicKeys.put(id, new CacheEntry<>(currentTime, key));
                        return Optional.of(key);
                    }
                }
            }
            cacheEntry = new CacheEntry<>(currentTime, null);
            publicKeys.put(id, cacheEntry);
        }
        return Optional.ofNullable(cacheEntry.value);
    }

    @Override
    public Optional<PGPPublicKeyRing> findKeyRing(long id) {
        return guard.guardByKey(id, () -> {
            CacheEntry<PGPPublicKeyRing> cacheEntry = publicKeyRings.get(id);
            if (cacheEntry == null || hasExpired(cacheEntry)) {
                return buildOperationExecutor.call(new CallableBuildOperation<Optional<PGPPublicKeyRing>>() {
                    @Override
                    public BuildOperationDescriptor.Builder description() {
                        return BuildOperationDescriptor.displayName("Fetching public key")
                            .progressDisplayName("Downloading public key " + toHexString(id));
                    }

                    @Override
                    public Optional<PGPPublicKeyRing> call(BuildOperationContext context) {
                        Optional<PGPPublicKeyRing> result = delegate.findKeyRing(id);
                        long currentTime = timeProvider.getCurrentTime();
                        if (result.isPresent()) {
                            PGPPublicKeyRing pgpPublicKeys = result.get();
                            publicKeyRings.put(id, new CacheEntry<>(currentTime, pgpPublicKeys));
                            for (PGPPublicKey pgpPublicKey : pgpPublicKeys) {
                                publicKeys.put(pgpPublicKey.getKeyID(), new CacheEntry<>(currentTime, pgpPublicKey));
                            }
                        } else {
                            publicKeyRings.put(id, new CacheEntry<>(currentTime, null));
                        }
                        return result;
                    }
                });
            }
            return Optional.ofNullable(cacheEntry.value);
        });
    }

    private static class PublicKeyRingSerializer extends AbstractSerializer<CacheEntry<PGPPublicKeyRing>> {

        @Override
        public CacheEntry<PGPPublicKeyRing> read(Decoder decoder) throws Exception {
            long timestamp = decoder.readLong();
            boolean present = decoder.readBoolean();
            if (present) {
                byte[] encoded = decoder.readBinary();
                PGPObjectFactory objectFactory = new PGPObjectFactory(
                    PGPUtil.getDecoderStream(new ByteArrayInputStream(encoded)), new BcKeyFingerprintCalculator());
                Object object = objectFactory.nextObject();
                if (object instanceof PGPPublicKeyRing) {
                    return new CacheEntry<>(timestamp, (PGPPublicKeyRing) object);
                }
                throw new IllegalStateException("Unexpected key in cache: " + object.getClass());
            }
            return new CacheEntry<>(timestamp, null);
        }

        @Override
        public void write(Encoder encoder, CacheEntry<PGPPublicKeyRing> value) throws Exception {
            encoder.writeLong(value.timestamp);
            PGPPublicKeyRing key = value.value;
            if (key != null) {
                encoder.writeBoolean(true);
                encoder.writeBinary(key.getEncoded());
            } else {
                encoder.writeBoolean(false);
            }
        }
    }

    private static class PublicKeyEntrySerializer extends AbstractSerializer<CacheEntry<PGPPublicKey>> {
        private final PublicKeySerializer keySerializer = new PublicKeySerializer();

        @Override
        public CacheEntry<PGPPublicKey> read(Decoder decoder) throws Exception {
            long timestamp = decoder.readLong();
            boolean present = decoder.readBoolean();
            if (present) {
                return new CacheEntry<>(timestamp, keySerializer.read(decoder));
            } else {
                return new CacheEntry<>(timestamp, null);
            }
        }

        @Override
        public void write(Encoder encoder, CacheEntry<PGPPublicKey> value) throws Exception {
            encoder.writeLong(value.timestamp);
            PGPPublicKey key = value.value;
            if (key != null) {
                encoder.writeBoolean(true);
                keySerializer.write(encoder, key);
            } else {
                encoder.writeBoolean(false);
            }
        }

    }

    private static class CacheEntry<T> {
        private final long timestamp;
        private final T value;

        private CacheEntry(long timestamp, T value) {
            this.timestamp = timestamp;
            this.value = value;
        }
    }
}
