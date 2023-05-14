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
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.scopes.BuildScopedCacheBuilderFactory;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.resource.ExternalResourceRepository;
import org.gradle.internal.resource.local.FileResourceListener;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.security.internal.EmptyPublicKeyService;
import org.gradle.security.internal.Fingerprint;
import org.gradle.security.internal.PublicKeyDownloadService;
import org.gradle.security.internal.PublicKeyResultBuilder;
import org.gradle.security.internal.PublicKeyService;
import org.gradle.security.internal.SecuritySupport;
import org.gradle.util.internal.BuildCommencedTimeProvider;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.gradle.security.internal.SecuritySupport.toLongIdHexString;

@ServiceScope(Scopes.Build.class)
public class DefaultSignatureVerificationServiceFactory implements SignatureVerificationServiceFactory {

    private static final HashCode NO_KEYRING_FILE_HASH = Hashing.signature(DefaultSignatureVerificationServiceFactory.class);

    private final RepositoryTransportFactory transportFactory;
    private final GlobalScopedCacheBuilderFactory globalScopedCacheBuilderFactory;
    private final InMemoryCacheDecoratorFactory decoratorFactory;
    private final BuildOperationExecutor buildOperationExecutor;
    private final FileHasher fileHasher;
    private final BuildScopedCacheBuilderFactory buildScopedCacheBuilderFactory;
    private final BuildCommencedTimeProvider timeProvider;
    private final boolean refreshKeys;
    private final FileResourceListener fileResourceListener;

    public DefaultSignatureVerificationServiceFactory(
        RepositoryTransportFactory transportFactory,
        GlobalScopedCacheBuilderFactory globalScopedCacheBuilderFactory,
        InMemoryCacheDecoratorFactory decoratorFactory,
        BuildOperationExecutor buildOperationExecutor,
        FileHasher fileHasher,
        BuildScopedCacheBuilderFactory buildScopedCacheBuilderFactory,
        BuildCommencedTimeProvider timeProvider,
        boolean refreshKeys,
        FileResourceListener fileResourceListener
    ) {
        this.transportFactory = transportFactory;
        this.globalScopedCacheBuilderFactory = globalScopedCacheBuilderFactory;
        this.decoratorFactory = decoratorFactory;
        this.buildOperationExecutor = buildOperationExecutor;
        this.fileHasher = fileHasher;
        this.buildScopedCacheBuilderFactory = buildScopedCacheBuilderFactory;
        this.timeProvider = timeProvider;
        this.refreshKeys = refreshKeys;
        this.fileResourceListener = fileResourceListener;
    }

    @Override
    public SignatureVerificationService create(BuildTreeDefinedKeys keyrings, List<URI> keyServers, boolean useKeyServers) {
        boolean refreshKeys = this.refreshKeys || !useKeyServers;
        ExternalResourceRepository repository = transportFactory.createTransport("https", "https", Collections.emptyList(), redirectLocations -> {}).getRepository();
        PublicKeyService keyService;
        if (useKeyServers) {
            PublicKeyDownloadService keyDownloadService = new PublicKeyDownloadService(ImmutableList.copyOf(keyServers), repository);
            keyService = new CrossBuildCachingKeyService(globalScopedCacheBuilderFactory, decoratorFactory, buildOperationExecutor, keyDownloadService, timeProvider, refreshKeys);
        } else {
            keyService = EmptyPublicKeyService.getInstance();
        }
        keyService = keyrings.applyTo(keyService);
        File effectiveKeyringsFile = keyrings.getEffectiveKeyringsFile();
        HashCode keyringFileHash = effectiveKeyringsFile != null && observed(effectiveKeyringsFile).exists()
            ? fileHasher.hash(effectiveKeyringsFile)
            : NO_KEYRING_FILE_HASH;
        DefaultSignatureVerificationService delegate = new DefaultSignatureVerificationService(keyService);
        return new CrossBuildSignatureVerificationService(
            delegate,
            fileHasher,
            buildScopedCacheBuilderFactory,
            decoratorFactory,
            timeProvider,
            refreshKeys,
            useKeyServers,
            keyringFileHash
        );
    }

    private File observed(File file) {
        fileResourceListener.fileObserved(file);
        return file;
    }

    private static class DefaultSignatureVerificationService implements SignatureVerificationService {
        private final PublicKeyService keyService;

        public DefaultSignatureVerificationService(PublicKeyService keyService) {
            this.keyService = keyService;
        }

        @Override
        public void verify(File origin, File signature, Set<String> trustedKeys, Set<String> ignoredKeys, SignatureVerificationResultBuilder result) {
            PGPSignatureList pgpSignatures = SecuritySupport.readSignatures(signature);
            for (PGPSignature pgpSignature : pgpSignatures) {
                String longIdKey = toLongIdHexString(pgpSignature.getKeyID());
                if (ignoredKeys.contains(longIdKey)) {
                    result.ignored(longIdKey);
                    continue;
                }
                AtomicBoolean missing = new AtomicBoolean(true);
                keyService.findByLongId(pgpSignature.getKeyID(), new PublicKeyResultBuilder() {
                    @Override
                    public void keyRing(PGPPublicKeyRing keyring) {

                    }

                    @Override
                    public void publicKey(PGPPublicKey pgpPublicKey) {
                        missing.set(false);
                        String fingerprint = Fingerprint.of(pgpPublicKey).toString();
                        if (ignoredKeys.contains(fingerprint)) {
                            result.ignored(fingerprint);
                            return;
                        }
                        try {
                            boolean verified = SecuritySupport.verify(origin, pgpSignature, pgpPublicKey);
                            if (!verified) {
                                result.failed(pgpPublicKey);
                            } else {
                                boolean trusted = trustedKeys.contains(fingerprint) || trustedKeys.contains(toLongIdHexString(pgpPublicKey.getKeyID()));
                                result.verified(pgpPublicKey, trusted);
                            }
                        } catch (PGPException e) {
                            throw UncheckedException.throwAsUncheckedException(e);
                        }
                    }
                });
                if (missing.get()) {
                    result.missingKey(longIdKey);
                }
            }
        }

        @Override
        public PublicKeyService getPublicKeyService() {
            return keyService;
        }

        @Override
        public void stop() {
            try {
                keyService.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
