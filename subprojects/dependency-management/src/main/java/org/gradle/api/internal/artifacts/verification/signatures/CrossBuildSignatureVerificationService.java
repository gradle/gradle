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

import com.google.common.collect.Lists;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.IndexedCacheParameters;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.scopes.BuildScopedCacheBuilderFactory;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.InterningStringSerializer;
import org.gradle.internal.serialize.SetSerializer;
import org.gradle.security.internal.PublicKeyService;
import org.gradle.util.internal.BuildCommencedTimeProvider;

import java.io.File;
import java.util.List;
import java.util.Set;

import static org.gradle.api.internal.artifacts.verification.signatures.CrossBuildCachingKeyService.MISSING_KEY_TIMEOUT;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class CrossBuildSignatureVerificationService implements SignatureVerificationService {
    private final SignatureVerificationService delegate;
    private final FileHasher fileHasher;
    private final BuildCommencedTimeProvider timeProvider;
    private final boolean refreshKeys;
    private final PersistentCache store;
    private final IndexedCache<CacheKey, CacheEntry> cache;
    private final boolean useKeyServers;
    private final HashCode keyringFileHash;

    public CrossBuildSignatureVerificationService(SignatureVerificationService delegate,
                                                  FileHasher fileHasher,
                                                  BuildScopedCacheBuilderFactory cacheBuilderFactory,
                                                  InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory,
                                                  BuildCommencedTimeProvider timeProvider,
                                                  boolean refreshKeys,
                                                  boolean useKeyServers,
                                                  HashCode keyringFileHash) {
        this.delegate = delegate;
        this.fileHasher = fileHasher;
        this.timeProvider = timeProvider;
        this.refreshKeys = refreshKeys;
        this.useKeyServers = useKeyServers;
        this.keyringFileHash = keyringFileHash;
        store = cacheBuilderFactory.createCacheBuilder("signature-verification")
            .withDisplayName("Signature verification cache")
            .withLockOptions(mode(FileLockManager.LockMode.OnDemand)) // Lock on demand
            .open();
        InterningStringSerializer stringSerializer = new InterningStringSerializer(new StringInterner());
        cache = store.createIndexedCache(
            IndexedCacheParameters.of(
                "signature-verification",
                new CacheKeySerializer(stringSerializer, new SetSerializer<>(stringSerializer)),
                new CacheEntrySerializer(stringSerializer)
            ).withCacheDecorator(inMemoryCacheDecoratorFactory.decorator(500, true)));
    }

    @Override
    public void verify(File origin, File signature, Set<String> trustedKeys, Set<String> ignoredKeys, SignatureVerificationResultBuilder builder) {
        CacheKey cacheKey = new CacheKey(origin.getAbsolutePath(), signature.getAbsolutePath(), trustedKeys, ignoredKeys, useKeyServers, keyringFileHash);
        HashCode originHash = fileHasher.hash(origin);
        HashCode signatureHash = fileHasher.hash(signature);
        CacheEntry entry = cache.getIfPresent(cacheKey);
        if (entry == null || entry.updated(originHash, signatureHash) || hasExpired(entry)) {
            entry = performActualVerification(origin, signature, trustedKeys, ignoredKeys, originHash, signatureHash);
            cache.put(cacheKey, entry);
        }
        entry.applyTo(builder);
    }

    private boolean hasExpired(CacheEntry entry) {
        List<String> missingKeys = entry.missingKeys;
        if (missingKeys == null || missingKeys.isEmpty()) {
            return false;
        }
        long elapsed = timeProvider.getCurrentTime() - entry.timestamp;
        return refreshKeys || elapsed > MISSING_KEY_TIMEOUT;
    }

    @Override
    public PublicKeyService getPublicKeyService() {
        return delegate.getPublicKeyService();
    }

    private CacheEntry performActualVerification(File origin, File signature, Set<String> trustedKeys, Set<String> ignoredKeys, HashCode originHash, HashCode signatureHash) {
        CacheEntryBuilder result = new CacheEntryBuilder(timeProvider.getCurrentTime(), originHash, signatureHash);
        delegate.verify(origin, signature, trustedKeys, ignoredKeys, result);
        return result.build();
    }

    @Override
    public void stop() {
        delegate.stop();
        store.close();
    }

    private static class CacheKey {
        private final String filePath;
        private final String signaturePath;
        private final Set<String> trustedKeys;
        private final Set<String> ignoredKeys;
        private final boolean useKeyServers;
        private final HashCode keyringFileHash;

        private CacheKey(String filePath, String signaturePath, Set<String> trustedKeys, Set<String> ignoredKeys, boolean useKeyServers, HashCode keyringFileHash) {
            this.filePath = filePath;
            this.signaturePath = signaturePath;
            this.trustedKeys = trustedKeys;
            this.ignoredKeys = ignoredKeys;
            this.useKeyServers = useKeyServers;
            this.keyringFileHash = keyringFileHash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CacheKey cacheKey = (CacheKey) o;

            if (!filePath.equals(cacheKey.filePath)) {
                return false;
            }
            if (!signaturePath.equals(cacheKey.signaturePath)) {
                return false;
            }
            if (!trustedKeys.equals(cacheKey.trustedKeys)) {
                return false;
            }
            if (!ignoredKeys.equals(cacheKey.ignoredKeys)) {
                return false;
            }
            if (useKeyServers != cacheKey.useKeyServers) {
                return false;
            }
            return keyringFileHash.equals(cacheKey.keyringFileHash);
        }

        @Override
        public int hashCode() {
            int result = filePath.hashCode();
            result = 31 * result + signaturePath.hashCode();
            result = 31 * result + trustedKeys.hashCode();
            result = 31 * result + ignoredKeys.hashCode();
            result = 31 * result + Boolean.hashCode(useKeyServers);
            result = 31 * result + keyringFileHash.hashCode();
            return result;
        }
    }

    private static class CacheKeySerializer extends AbstractSerializer<CacheKey> {
        private final InterningStringSerializer delegate;
        private final SetSerializer<String> setSerializer;
        private final HashCodeSerializer hashCodeSerializer;

        private CacheKeySerializer(InterningStringSerializer stringSerializer, SetSerializer<String> setSerializer) {
            this.delegate = stringSerializer;
            this.setSerializer = setSerializer;
            this.hashCodeSerializer = new HashCodeSerializer();
        }

        @Override
        public CacheKey read(Decoder decoder) throws Exception {
            return new CacheKey(delegate.read(decoder), delegate.read(decoder), setSerializer.read(decoder), setSerializer.read(decoder), decoder.readBoolean(), hashCodeSerializer.read(decoder));
        }

        @Override
        public void write(Encoder encoder, CacheKey value) throws Exception {
            delegate.write(encoder, value.filePath);
            delegate.write(encoder, value.signaturePath);
            setSerializer.write(encoder, value.trustedKeys);
            setSerializer.write(encoder, value.ignoredKeys);
            encoder.writeBoolean(value.useKeyServers);
            hashCodeSerializer.write(encoder, value.keyringFileHash);
        }
    }

    private static class CacheEntryBuilder implements SignatureVerificationResultBuilder {
        private final long timestamp;
        private final HashCode originHash;
        private final HashCode signatureHash;

        private List<String> missingKeys = null;
        private List<PGPPublicKey> trustedKeys = null;
        private List<PGPPublicKey> validKeys = null;
        private List<PGPPublicKey> failedKeys = null;
        private List<String> ignoredKeys = null;

        private CacheEntryBuilder(long timestamp, HashCode originHash, HashCode signatureHash) {
            this.timestamp = timestamp;
            this.originHash = originHash;
            this.signatureHash = signatureHash;
        }

        @Override
        public void missingKey(String keyId) {
            if (missingKeys == null) {
                missingKeys = Lists.newArrayList();
            }
            missingKeys.add(keyId);
        }

        @Override
        public void verified(PGPPublicKey key, boolean trusted) {
            if (trusted) {
                if (trustedKeys == null) {
                    trustedKeys = Lists.newArrayList();
                }
                trustedKeys.add(key);
            } else {
                if (validKeys == null) {
                    validKeys = Lists.newArrayList();
                }
                validKeys.add(key);
            }
        }

        @Override
        public void failed(PGPPublicKey pgpPublicKey) {
            if (failedKeys == null) {
                failedKeys = Lists.newArrayList();
            }
            failedKeys.add(pgpPublicKey);
        }

        @Override
        public void ignored(String keyId) {
            if (ignoredKeys == null) {
                ignoredKeys = Lists.newArrayList();
            }
            ignoredKeys.add(keyId);
        }

        CacheEntry build() {
            return new CacheEntry(timestamp, originHash, signatureHash, missingKeys, trustedKeys, validKeys, failedKeys, ignoredKeys);
        }
    }

    private static class CacheEntry {
        private final long timestamp;
        private final HashCode originHash;
        private final HashCode signatureHash;
        private final List<String> missingKeys;
        private final List<PGPPublicKey> trustedKeys;
        private final List<PGPPublicKey> validKeys;
        private final List<PGPPublicKey> failedKeys;
        private final List<String> ignoredKeys;

        public CacheEntry(long timestamp, HashCode originHash, HashCode signatureHash, List<String> missingKeys, List<PGPPublicKey> trustedKeys, List<PGPPublicKey> validKeys, List<PGPPublicKey> failedKeys, List<String> ignoredKeys) {
            this.timestamp = timestamp;
            this.originHash = originHash;
            this.signatureHash = signatureHash;
            this.missingKeys = missingKeys;
            this.trustedKeys = trustedKeys;
            this.validKeys = validKeys;
            this.failedKeys = failedKeys;
            this.ignoredKeys = ignoredKeys;
        }

        void applyTo(SignatureVerificationResultBuilder builder) {
            if (missingKeys != null) {
                for (String missingKey : missingKeys) {
                    builder.missingKey(missingKey);
                }
            }
            if (trustedKeys != null) {
                for (PGPPublicKey trustedKey : trustedKeys) {
                    builder.verified(trustedKey, true);
                }
            }
            if (validKeys != null) {
                for (PGPPublicKey validKey : validKeys) {
                    builder.verified(validKey, false);
                }
            }
            if (failedKeys != null) {
                for (PGPPublicKey failedKey : failedKeys) {
                    builder.failed(failedKey);
                }
            }
            if (ignoredKeys != null) {
                for (String ignoredKey : ignoredKeys) {
                    builder.ignored(ignoredKey);
                }
            }
        }

        public boolean updated(HashCode originHash, HashCode signatureHash) {
            return !this.originHash.equals(originHash) ||
                !this.signatureHash.equals(signatureHash);
        }
    }

    private static class CacheEntrySerializer extends AbstractSerializer<CacheEntry> {
        private final InterningStringSerializer stringSerializer;
        private final PublicKeySerializer publicKeySerializer = new PublicKeySerializer();

        private CacheEntrySerializer(InterningStringSerializer stringSerializer) {
            this.stringSerializer = stringSerializer;
        }

        @Override
        public CacheEntry read(Decoder decoder) throws Exception {
            long timestamp = decoder.readLong();
            HashCode originHash = HashCode.fromBytes(decoder.readBinary());
            HashCode signatureHash = HashCode.fromBytes(decoder.readBinary());
            List<String> missingKeys = readStringKeys(decoder);
            List<PGPPublicKey> trustedKeys = readKeys(decoder);
            List<PGPPublicKey> validKeys = readKeys(decoder);
            List<PGPPublicKey> failedKeys = readKeys(decoder);
            List<String> ignoredKeys = readStringKeys(decoder);
            return new CacheEntry(timestamp, originHash, signatureHash, missingKeys, trustedKeys, validKeys, failedKeys, ignoredKeys);
        }

        private List<String> readStringKeys(Decoder decoder) throws Exception {
            int missingKeysLen = decoder.readSmallInt();
            List<String> missingKeys = null;
            if (missingKeysLen > 0) {
                missingKeys = Lists.newArrayListWithCapacity(missingKeysLen);
                for (int i = 0; i < missingKeysLen; i++) {
                    missingKeys.add(stringSerializer.read(decoder));
                }
            }
            return missingKeys;
        }

        private List<PGPPublicKey> readKeys(Decoder decoder) throws Exception {
            int len = decoder.readSmallInt();
            List<PGPPublicKey> keys = null;
            if (len > 0) {
                keys = Lists.newArrayListWithCapacity(len);
                for (int i = 0; i < len; i++) {
                    keys.add(publicKeySerializer.read(decoder));
                }
            }
            return keys;
        }

        @Override
        public void write(Encoder encoder, CacheEntry value) throws Exception {
            encoder.writeLong(value.timestamp);
            encoder.writeBinary(value.originHash.toByteArray());
            encoder.writeBinary(value.signatureHash.toByteArray());
            writeStringKeys(encoder, value.missingKeys);
            writeKeys(encoder, value.trustedKeys);
            writeKeys(encoder, value.validKeys);
            writeKeys(encoder, value.failedKeys);
            writeStringKeys(encoder, value.ignoredKeys);
        }

        private void writeStringKeys(Encoder encoder, List<String> keys) throws Exception {
            if (keys == null) {
                encoder.writeSmallInt(0);
            } else {
                encoder.writeSmallInt(keys.size());
                for (String key : keys) {
                    stringSerializer.write(encoder, key);
                }
            }
        }

        private void writeKeys(Encoder encoder, List<PGPPublicKey> keys) throws Exception {
            if (keys == null) {
                encoder.writeSmallInt(0);
            } else {
                encoder.writeSmallInt(keys.size());
                for (PGPPublicKey key : keys) {
                    publicKeySerializer.write(encoder, key);
                }
            }
        }
    }

}
