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

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.util.Optional;

public class CrossBuildCachingKeyService implements PublicKeyService, Closeable {

    private final PersistentCache cache;
    private final BuildOperationExecutor buildOperationExecutor;
    private final PublicKeyService delegate;
    private final PersistentIndexedCache<Long, PGPPublicKey> publicKeys;
    private final PersistentIndexedCache<Long, PGPPublicKeyRing> publicKeyRings;

    public CrossBuildCachingKeyService(CacheRepository cacheRepository,
                                       InMemoryCacheDecoratorFactory decoratorFactory,
                                       BuildOperationExecutor buildOperationExecutor,
                                       PublicKeyService delegate) {
        cache = cacheRepository
            .cache("signatures")
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
            .withLockOptions(LockOptionsBuilder.mode(FileLockManager.LockMode.None))
            .open();
        this.buildOperationExecutor = buildOperationExecutor;
        this.delegate = delegate;
        PersistentIndexedCacheParameters<Long, PGPPublicKey> publicKeyParams = PersistentIndexedCacheParameters.of(
            "publickeys",
            BaseSerializerFactory.LONG_SERIALIZER,
            new PublicKeySerializer()
        ).withCacheDecorator(
            decoratorFactory.decorator(2000, true)
        );
        publicKeys = cache.createCache(publicKeyParams);

        PersistentIndexedCacheParameters<Long, PGPPublicKeyRing> keyringParams = PersistentIndexedCacheParameters.of(
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

    @Override
    public Optional<PGPPublicKey> findPublicKey(long id) {
        PGPPublicKey pgpPublicKey = publicKeys.get(id);
        if (pgpPublicKey == null) {
            return buildOperationExecutor.call(new CallableBuildOperation<Optional<PGPPublicKey>>() {
                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Fetching public key " + id);
                }

                @Override
                public Optional<PGPPublicKey> call(BuildOperationContext context) {
                    Optional<PGPPublicKey> result = delegate.findPublicKey(id);
                    result.ifPresent(key -> publicKeys.put(id, key));
                    return result;
                }
            });
        }
        return Optional.of(pgpPublicKey);
    }

    @Override
    public Optional<PGPPublicKeyRing> findKeyRing(long id) {
        PGPPublicKeyRing pgpPublicKeyRing = publicKeyRings.get(id);
        if (pgpPublicKeyRing == null) {
            return buildOperationExecutor.call(new CallableBuildOperation<Optional<PGPPublicKeyRing>>() {
                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Fetching public key " + id);
                }

                @Override
                public Optional<PGPPublicKeyRing> call(BuildOperationContext context) {
                    Optional<PGPPublicKeyRing> result = delegate.findKeyRing(id);
                    result.ifPresent(key -> publicKeyRings.put(id, key));
                    return result;
                }
            });
        }
        return Optional.of(pgpPublicKeyRing);
    }

    private static class PublicKeyRingSerializer extends AbstractSerializer<PGPPublicKeyRing> {

        @Override
        public PGPPublicKeyRing read(Decoder decoder) throws Exception {
            byte[] encoded = decoder.readBinary();
            PGPObjectFactory objectFactory = new PGPObjectFactory(
                PGPUtil.getDecoderStream(new ByteArrayInputStream(encoded)), new BcKeyFingerprintCalculator());
            Object object = objectFactory.nextObject();
            if (object instanceof PGPPublicKeyRing) {
                return (PGPPublicKeyRing) object;
            }
            throw new IllegalStateException("Unexpected key in cache: " + object.getClass());
        }

        @Override
        public void write(Encoder encoder, PGPPublicKeyRing value) throws Exception {
            encoder.writeBinary(value.getEncoded());
        }
    }

    private static class PublicKeySerializer extends AbstractSerializer<PGPPublicKey> {

        @Override
        public PGPPublicKey read(Decoder decoder) throws Exception {
            byte[] encoded = decoder.readBinary();
            PGPObjectFactory objectFactory = new PGPObjectFactory(
                PGPUtil.getDecoderStream(new ByteArrayInputStream(encoded)), new BcKeyFingerprintCalculator());
            Object object = objectFactory.nextObject();
            if (object instanceof PGPPublicKey) {
                return (PGPPublicKey) object;
            } else if (object instanceof PGPPublicKeyRing) {
                return ((PGPPublicKeyRing) object).getPublicKey();
            }
            throw new IllegalStateException("Unexpected key in cache: " + object.getClass());
        }

        @Override
        public void write(Encoder encoder, PGPPublicKey value) throws Exception {
            encoder.writeBinary(value.getEncoded());
        }

    }
}
