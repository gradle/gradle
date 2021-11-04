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
import org.gradle.cache.scopes.BuildScopedCache;
import org.gradle.cache.scopes.GlobalScopedCache;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.resource.ExternalResourceRepository;
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
    private final RepositoryTransportFactory transportFactory;
    private final GlobalScopedCache cacheRepository;
    private final InMemoryCacheDecoratorFactory decoratorFactory;
    private final BuildOperationExecutor buildOperationExecutor;
    private final FileHasher fileHasher;
    private final BuildScopedCache buildScopedCache;
    private final BuildCommencedTimeProvider timeProvider;
    private final boolean refreshKeys;

    public DefaultSignatureVerificationServiceFactory(RepositoryTransportFactory transportFactory,
                                                      GlobalScopedCache cacheRepository,
                                                      InMemoryCacheDecoratorFactory decoratorFactory,
                                                      BuildOperationExecutor buildOperationExecutor,
                                                      FileHasher fileHasher,
                                                      BuildScopedCache buildScopedCache,
                                                      BuildCommencedTimeProvider timeProvider,
                                                      boolean refreshKeys) {
        this.transportFactory = transportFactory;
        this.cacheRepository = cacheRepository;
        this.decoratorFactory = decoratorFactory;
        this.buildOperationExecutor = buildOperationExecutor;
        this.fileHasher = fileHasher;
        this.buildScopedCache = buildScopedCache;
        this.timeProvider = timeProvider;
        this.refreshKeys = refreshKeys;
    }

    @Override
    public SignatureVerificationService create(BuildTreeDefinedKeys keyrings, List<URI> keyServers, boolean useKeyServers) {
        boolean refreshKeys = this.refreshKeys || !useKeyServers;
        ExternalResourceRepository repository = transportFactory.createTransport("https", "https", Collections.emptyList(), redirectLocations -> {}).getRepository();
        PublicKeyService keyService;
        if (useKeyServers) {
            PublicKeyDownloadService keyDownloadService = new PublicKeyDownloadService(ImmutableList.copyOf(keyServers), repository);
            keyService = new CrossBuildCachingKeyService(cacheRepository, decoratorFactory, buildOperationExecutor, keyDownloadService, timeProvider, refreshKeys);
        } else {
            keyService = EmptyPublicKeyService.getInstance();
        }
        keyService = keyrings.applyTo(keyService);
        DefaultSignatureVerificationService delegate = new DefaultSignatureVerificationService(keyService);
        return new CrossBuildSignatureVerificationService(
            delegate,
            fileHasher,
            buildScopedCache,
            decoratorFactory,
            timeProvider,
            refreshKeys
        );
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
