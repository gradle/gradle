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

import com.google.common.collect.ImmutableList;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.IndexedCacheParameters;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.cache.internal.filelock.LockOptionsBuilder;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.ListSerializer;
import org.gradle.security.internal.Fingerprint;
import org.gradle.security.internal.PublicKeyResultBuilder;
import org.gradle.security.internal.PublicKeyService;
import org.gradle.util.internal.BuildCommencedTimeProvider;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.gradle.security.internal.SecuritySupport.toLongIdHexString;

public class CrossBuildCachingKeyService implements PublicKeyService, Closeable {
    final static long MISSING_KEY_TIMEOUT = TimeUnit.MILLISECONDS.convert(24, TimeUnit.HOURS);

    private final PersistentCache cache;
    private final BuildOperationExecutor buildOperationExecutor;
    private final PublicKeyService delegate;
    private final BuildCommencedTimeProvider timeProvider;
    private final boolean refreshKeys;
    private final IndexedCache<Fingerprint, CacheEntry<PGPPublicKeyRing>> publicKeyRings;
    // Some long key Id may have collisions. This is extremely unlikely but if it happens, we know how to workaround
    private final IndexedCache<Long, CacheEntry<List<Fingerprint>>> longIdToFingerprint;
    private final ProducerGuard<Fingerprint> fingerPrintguard = ProducerGuard.adaptive();
    private final ProducerGuard<Long> longIdGuard = ProducerGuard.adaptive();

    public CrossBuildCachingKeyService(
            GlobalScopedCacheBuilderFactory cacheBuilderFactory,
            InMemoryCacheDecoratorFactory decoratorFactory,
            BuildOperationExecutor buildOperationExecutor,
            PublicKeyService delegate,
            BuildCommencedTimeProvider timeProvider,
            boolean refreshKeys) {
        cache = cacheBuilderFactory
            .createCrossVersionCacheBuilder("keyrings")
            .withCrossVersionCache()
            .withLockOptions(new LockOptionsBuilder())
            .open();
        this.buildOperationExecutor = buildOperationExecutor;
        this.delegate = delegate;
        this.timeProvider = timeProvider;
        this.refreshKeys = refreshKeys;
        FingerprintSerializer fingerprintSerializer = new FingerprintSerializer();
        IndexedCacheParameters<Fingerprint, CacheEntry<PGPPublicKeyRing>> keyringParams = IndexedCacheParameters.of(
            "publickeyrings",
            fingerprintSerializer,
            new PublicKeyRingCacheEntrySerializer()
        ).withCacheDecorator(
            decoratorFactory.decorator(2000, true)
        );
        publicKeyRings = cache.createIndexedCache(keyringParams);

        IndexedCacheParameters<Long, CacheEntry<List<Fingerprint>>> mappingParameters = IndexedCacheParameters.of(
            "keymappings",
            BaseSerializerFactory.LONG_SERIALIZER,
            new FingerprintListCacheEntrySerializer(new ListSerializer<>(fingerprintSerializer))
        ).withCacheDecorator(
            decoratorFactory.decorator(2000, true)
        );
        longIdToFingerprint = cache.createIndexedCache(mappingParameters);
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
        long elapsed = timeProvider.getCurrentTime() - key.timestamp;
        return refreshKeys || elapsed > MISSING_KEY_TIMEOUT;
    }

    @Override
    public void findByLongId(long keyId, PublicKeyResultBuilder builder) {
        longIdGuard.guardByKey(keyId, () -> {
            CacheEntry<List<Fingerprint>> fingerprints = longIdToFingerprint.getIfPresent(keyId);
            if (fingerprints == null || hasExpired(fingerprints)) {
                buildOperationExecutor.run(new RunnableBuildOperation() {
                    @Override
                    public void run(BuildOperationContext context) {
                        long currentTime = timeProvider.getCurrentTime();
                        AtomicBoolean missing = new AtomicBoolean(true);
                        delegate.findByLongId(keyId, new PublicKeyResultBuilder() {
                            @Override
                            public void keyRing(PGPPublicKeyRing keyring) {
                                missing.set(false);
                                builder.keyRing(keyring);
                                Iterator<PGPPublicKey> pkIt = keyring.getPublicKeys();
                                while (pkIt.hasNext()) {
                                    PGPPublicKey publicKey = pkIt.next();
                                    Fingerprint fingerprint = Fingerprint.of(publicKey);
                                    publicKeyRings.put(fingerprint, new CacheEntry<>(currentTime, keyring));
                                    updateLongKeyIndex(fingerprint, keyId);
                                }
                            }

                            @Override
                            public void publicKey(PGPPublicKey publicKey) {
                                missing.set(false);
                                if (publicKey.getKeyID() == keyId) {
                                    builder.publicKey(publicKey);
                                }
                            }
                        });
                        if (missing.get()) {
                            longIdToFingerprint.put(keyId, new CacheEntry<>(currentTime, null));
                        }
                    }

                    @Override
                    public BuildOperationDescriptor.Builder description() {
                        return BuildOperationDescriptor.displayName("Fetching public key")
                            .progressDisplayName("Downloading public key " + toLongIdHexString(keyId));
                    }
                });
            } else {
                if (fingerprints.value != null) {
                    for (Fingerprint fingerprint : fingerprints.value) {
                        findByFingerprint(fingerprint.getBytes(), new PublicKeyResultBuilder() {
                            @Override
                            public void keyRing(PGPPublicKeyRing keyring) {
                                builder.keyRing(keyring);
                            }

                            @Override
                            public void publicKey(PGPPublicKey publicKey) {
                                if (publicKey.getKeyID() == keyId) {
                                    builder.publicKey(publicKey);
                                }
                            }
                        });
                    }
                }
            }
            return null;
        });
    }

    private void updateLongKeyIndex(Fingerprint fingerprint, long keyId) {
        CacheEntry<List<Fingerprint>> fprints = longIdToFingerprint.getIfPresent(keyId);
        long currentTime = timeProvider.getCurrentTime();
        if (fprints == null) {
            longIdToFingerprint.put(keyId, new CacheEntry<>(currentTime, Collections.singletonList(fingerprint)));
        } else {
            longIdToFingerprint.remove(keyId);
            ImmutableList.Builder<Fingerprint> list = ImmutableList.builderWithExpectedSize(1 + fprints.value.size());
            list.addAll(fprints.value);
            list.add(fingerprint);
            longIdToFingerprint.put(keyId, new CacheEntry<>(currentTime, list.build()));
        }
    }

    @Override
    public void findByFingerprint(byte[] bytes, PublicKeyResultBuilder builder) {
        Fingerprint fingerprint = Fingerprint.wrap(bytes);
        fingerPrintguard.guardByKey(fingerprint, () -> {
            CacheEntry<PGPPublicKeyRing> cacheEntry = publicKeyRings.getIfPresent(fingerprint);
            if (cacheEntry == null || hasExpired(cacheEntry)) {
                LookupPublicKeyResultBuilder keyResultBuilder = new LookupPublicKeyResultBuilder();
                delegate.findByFingerprint(bytes, keyResultBuilder);
                cacheEntry = keyResultBuilder.entry;
            }
            if (cacheEntry != null) {
                builder.keyRing(cacheEntry.value);
                Iterator<PGPPublicKey> pkIt = cacheEntry.value.getPublicKeys();
                while (pkIt.hasNext()) {
                    PGPPublicKey publicKey = pkIt.next();
                    if (Arrays.equals(publicKey.getFingerprint(), bytes)) {
                        builder.publicKey(publicKey);
                    }
                }
            }
            return null;
        });
    }

    private static class PublicKeyRingCacheEntrySerializer extends AbstractSerializer<CacheEntry<PGPPublicKeyRing>> {

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

    private static class CacheEntry<T> {
        private final long timestamp;
        private final T value;

        private CacheEntry(long timestamp, T value) {
            this.timestamp = timestamp;
            this.value = value;
        }
    }

    private static class FingerprintSerializer extends AbstractSerializer<Fingerprint> {

        @Override
        public Fingerprint read(Decoder decoder) throws Exception {
            return Fingerprint.wrap(decoder.readBinary());
        }

        @Override
        public void write(Encoder encoder, Fingerprint value) throws Exception {
            encoder.writeBinary(value.getBytes());
        }
    }

    private class LookupPublicKeyResultBuilder implements PublicKeyResultBuilder {
        CacheEntry<PGPPublicKeyRing> entry;

        @Override
        public void keyRing(PGPPublicKeyRing keyring) {
            entry = new CacheEntry<>(timeProvider.getCurrentTime(), keyring);
            Iterator<PGPPublicKey> pkIt = keyring.getPublicKeys();
            while (pkIt.hasNext()) {
                PGPPublicKey publicKey = pkIt.next();
                Fingerprint fingerprint = Fingerprint.of(publicKey);
                long keyID = publicKey.getKeyID();
                updateLongKeyIndex(fingerprint, keyID);
            }
        }

        @Override
        public void publicKey(PGPPublicKey publicKey) {
        }
    }

    private static class FingerprintListCacheEntrySerializer extends AbstractSerializer<CacheEntry<List<Fingerprint>>> {
        private final ListSerializer<Fingerprint> listSerializer;

        public FingerprintListCacheEntrySerializer(ListSerializer<Fingerprint> listSerializer) {
            this.listSerializer = listSerializer;
        }

        @Override
        public CacheEntry<List<Fingerprint>> read(Decoder decoder) throws EOFException, Exception {
            long timestamp = decoder.readLong();
            List<Fingerprint> fingerprints = decoder.readBoolean() ? listSerializer.read(decoder) : null;
            return new CacheEntry<>(timestamp, fingerprints);
        }

        @Override
        public void write(Encoder encoder, CacheEntry<List<Fingerprint>> value) throws Exception {
            encoder.writeLong(value.timestamp);
            List<Fingerprint> fingerprints = value.value;
            if (fingerprints == null) {
                encoder.writeBoolean(false);
            } else {
                encoder.writeBoolean(true);
                listSerializer.write(encoder, fingerprints);
            }
        }
    }
}
