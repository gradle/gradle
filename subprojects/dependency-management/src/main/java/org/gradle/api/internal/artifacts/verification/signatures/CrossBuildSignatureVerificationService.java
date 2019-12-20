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

import com.google.common.collect.ImmutableMap;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.VersionStrategy;
import org.gradle.initialization.layout.ProjectCacheDir;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.InterningStringSerializer;
import org.gradle.internal.serialize.SetSerializer;
import org.gradle.security.internal.PublicKeyService;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class CrossBuildSignatureVerificationService implements SignatureVerificationService {
    private final SignatureVerificationService delegate;
    private final FileHasher fileHasher;
    private final boolean refreshKeys;
    private final PersistentCache store;
    private final PersistentIndexedCache<CacheKey, CacheEntry> cache;

    public CrossBuildSignatureVerificationService(SignatureVerificationService delegate,
                                                  FileHasher fileHasher,
                                                  CacheScopeMapping cacheScopeMapping,
                                                  ProjectCacheDir projectCacheDir,
                                                  CacheRepository repository,
                                                  InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory,
                                                  PublicKeyService keyService,
                                                  boolean refreshKeys) {
        this.delegate = delegate;
        this.fileHasher = fileHasher;
        this.refreshKeys = refreshKeys;
        File cacheDir = cacheScopeMapping.getBaseDirectory(projectCacheDir.getDir(), "signature-verification", VersionStrategy.CachePerVersion);
        store = repository.cache(cacheDir)
            .withDisplayName("Signature verification cache")
            .withLockOptions(mode(FileLockManager.LockMode.None)) // Lock on demand
            .open();
        InterningStringSerializer stringSerializer = new InterningStringSerializer(new StringInterner());
        SignatureVerificationFailureSerializer signatureVerificationFailureSerializer = new SignatureVerificationFailureSerializer(
            stringSerializer,
            keyService
        );
        cache = store.createCache(
            PersistentIndexedCacheParameters.of(
                "signature-verification",
                new CacheKeySerializer(stringSerializer),
                new CacheEntrySerializer(new SetSerializer<String>(stringSerializer), signatureVerificationFailureSerializer)
            ).withCacheDecorator(inMemoryCacheDecoratorFactory.decorator(500, true)));
    }

    @Override
    public Optional<SignatureVerificationFailure> verify(File origin, File signature, Set<String> trustedKeys) {
        CacheKey cacheKey = new CacheKey(origin.getAbsolutePath(), signature.getAbsolutePath());
        HashCode originHash = fileHasher.hash(origin);
        HashCode signatureHash = fileHasher.hash(signature);
        CacheEntry entry = null;
        if (!refreshKeys) {
            entry = cache.get(cacheKey, key -> performActualVerification(origin, signature, trustedKeys, originHash, signatureHash));
        }
        if (entry == null || !entry.matches(originHash, signatureHash, trustedKeys)) {
            entry = performActualVerification(origin, signature, trustedKeys, originHash, signatureHash);
            cache.put(cacheKey, entry);
        }
        return Optional.ofNullable(entry.verificationFailure);
    }

    private CacheEntry performActualVerification(File origin, File signature, Set<String> trustedKeys, HashCode originHash, HashCode signatureHash) {
        Optional<SignatureVerificationFailure> verificationFailure = delegate.verify(origin, signature, trustedKeys);
        return new CacheEntry(originHash, signatureHash, trustedKeys, verificationFailure.orElse(null));
    }

    @Override
    public void stop() {
        delegate.stop();
        store.close();
    }

    private static class CacheKey {
        private final String filePath;
        private final String signaturePath;

        private CacheKey(String filePath, String signaturePath) {
            this.filePath = filePath;
            this.signaturePath = signaturePath;
        }
    }

    private static class CacheKeySerializer extends AbstractSerializer<CacheKey> {
        private final InterningStringSerializer delegate;

        private CacheKeySerializer(InterningStringSerializer delegate) {
            this.delegate = delegate;
        }

        @Override
        public CacheKey read(Decoder decoder) throws Exception {
            return new CacheKey(delegate.read(decoder), delegate.read(decoder));
        }

        @Override
        public void write(Encoder encoder, CacheKey value) throws Exception {
            delegate.write(encoder, value.filePath);
            delegate.write(encoder, value.signaturePath);
        }
    }

    private static class CacheEntry {
        private final HashCode originHash;
        private final HashCode signatureHash;
        private final Set<String> trustedKeys;
        private final SignatureVerificationFailure verificationFailure;

        private CacheEntry(HashCode originHash, HashCode signatureHash, Set<String> trustedKeys, SignatureVerificationFailure verificationFailure) {
            this.originHash = originHash;
            this.signatureHash = signatureHash;
            this.trustedKeys = trustedKeys;
            this.verificationFailure = verificationFailure;
        }

        private boolean matches(HashCode originHash, HashCode signatureHash, Set<String> trustedKeys) {
            return this.originHash.equals(originHash)
                && this.signatureHash.equals(signatureHash)
                && this.trustedKeys.equals(trustedKeys);
        }
    }

    private static class CacheEntrySerializer extends AbstractSerializer<CacheEntry> {
        private final SetSerializer<String> stringSetSerializer;
        private final SignatureVerificationFailureSerializer signatureVerificationFailureSerializer;

        private CacheEntrySerializer(SetSerializer<String> stringSetSerializer,
                                     SignatureVerificationFailureSerializer signatureVerificationFailureSerializer) {
            this.stringSetSerializer = stringSetSerializer;
            this.signatureVerificationFailureSerializer = signatureVerificationFailureSerializer;
        }

        @Override
        public CacheEntry read(Decoder decoder) throws Exception {
            HashCode originHash = HashCode.fromBytes(decoder.readBinary());
            HashCode signatureHash = HashCode.fromBytes(decoder.readBinary());
            Set<String> trustedKeys = stringSetSerializer.read(decoder);
            SignatureVerificationFailure verificationFailure = signatureVerificationFailureSerializer.read(decoder);
            return new CacheEntry(originHash, signatureHash, trustedKeys, verificationFailure);
        }

        @Override
        public void write(Encoder encoder, CacheEntry value) throws Exception {
            encoder.writeBinary(value.originHash.toByteArray());
            encoder.writeBinary(value.signatureHash.toByteArray());
            stringSetSerializer.write(encoder, value.trustedKeys);
            signatureVerificationFailureSerializer.write(encoder, value.verificationFailure);
        }
    }

    private static class SignatureVerificationFailureSerializer extends AbstractSerializer<SignatureVerificationFailure> {
        private final InterningStringSerializer stringSerializer;
        private final PublicKeyService publicKeyService;
        private final PublicKeySerializer publicKeySerializer;

        private SignatureVerificationFailureSerializer(InterningStringSerializer stringSerializer, PublicKeyService publicKeyService) {
            this.stringSerializer = stringSerializer;
            this.publicKeyService = publicKeyService;
            this.publicKeySerializer = new PublicKeySerializer();
        }

        @Override
        public SignatureVerificationFailure read(Decoder decoder) throws Exception {
            if (!decoder.readBoolean()) {
                return null;
            }
            int size = decoder.readSmallInt();
            ImmutableMap.Builder<String, SignatureVerificationFailure.SignatureError> errors = ImmutableMap.builderWithExpectedSize(size);
            for (int i = 0; i < size; i++) {
                String key = stringSerializer.read(decoder);
                SignatureVerificationFailure.FailureKind kind = SignatureVerificationFailure.FailureKind.values()[decoder.readSmallInt()];
                PGPPublicKey publicKey = null;
                if (decoder.readBoolean()) {
                    publicKey = publicKeySerializer.read(decoder);
                }
                errors.put(key, new SignatureVerificationFailure.SignatureError(
                    publicKey, kind
                ));
            }
            return new SignatureVerificationFailure(errors.build(), publicKeyService);
        }

        @Override
        public void write(Encoder encoder, SignatureVerificationFailure value) throws Exception {
            if (value == null) {
                encoder.writeBoolean(false);
                return;
            }
            encoder.writeBoolean(true);
            Map<String, SignatureVerificationFailure.SignatureError> errors = value.getErrors();
            encoder.writeSmallInt(errors.size());
            for (Map.Entry<String, SignatureVerificationFailure.SignatureError> entry : errors.entrySet()) {
                String key = entry.getKey();
                SignatureVerificationFailure.SignatureError error = entry.getValue();
                stringSerializer.write(encoder, key);
                encoder.writeSmallInt(error.getKind().ordinal());
                PGPPublicKey publicKey = error.getPublicKey();
                if (publicKey == null) {
                    encoder.writeBoolean(false);
                } else {
                    encoder.writeBoolean(true);
                    publicKeySerializer.write(encoder, publicKey);
                }
            }
        }
    }
}
