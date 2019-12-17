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
import com.google.common.collect.Maps;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.resource.connector.ResourceConnectorSpecification;
import org.gradle.internal.resource.transfer.ExternalResourceConnector;
import org.gradle.internal.resource.transport.http.HttpConnectorFactory;
import org.gradle.security.internal.PublicKeyDownloadService;
import org.gradle.security.internal.SecuritySupport;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure.FailureKind.FAILED;
import static org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure.FailureKind.KEY_NOT_FOUND;
import static org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure.FailureKind.PASSED_NOT_TRUSTED;

public class DefaultSignatureVerificationServiceFactory implements SignatureVerificationServiceFactory {
    private final HttpConnectorFactory httpConnectorFactory;
    private final CacheRepository cacheRepository;
    private final InMemoryCacheDecoratorFactory decoratorFactory;
    private final BuildOperationExecutor buildOperationExecutor;

    public DefaultSignatureVerificationServiceFactory(HttpConnectorFactory httpConnectorFactory, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory decoratorFactory, BuildOperationExecutor buildOperationExecutor) {
        this.httpConnectorFactory = httpConnectorFactory;
        this.cacheRepository = cacheRepository;
        this.decoratorFactory = decoratorFactory;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public SignatureVerificationService create(List<URI> keyServers) {
        ExternalResourceConnector connector = httpConnectorFactory.createResourceConnector(new ResourceConnectorSpecification() {
        });
        PublicKeyDownloadService keyDownloadService = new PublicKeyDownloadService(ImmutableList.copyOf(keyServers), connector);
        CrossBuildCachingKeyService keyService = new CrossBuildCachingKeyService(cacheRepository, decoratorFactory, buildOperationExecutor, keyDownloadService);
        return new DefaultSignatureVerificationService(keyService);
    }


    private static SignatureVerificationFailure.SignatureError error(@Nullable PGPPublicKey key, SignatureVerificationFailure.FailureKind kind) {
        return new SignatureVerificationFailure.SignatureError(key, kind);
    }

    private static class DefaultSignatureVerificationService implements SignatureVerificationService, Stoppable {
        private final CrossBuildCachingKeyService keyService;

        public DefaultSignatureVerificationService(CrossBuildCachingKeyService keyService) {
            this.keyService = keyService;
        }

        @Override
        public Optional<SignatureVerificationFailure> verify(File origin, File signature, Set<String> trustedKeys) {
            PGPSignatureList pgpSignatures = SecuritySupport.readSignatures(signature);
            Map<String, SignatureVerificationFailure.SignatureError> errors = Maps.newHashMap();
            for (PGPSignature pgpSignature : pgpSignatures) {
                String key = SecuritySupport.toHexString(pgpSignature.getKeyID());
                Optional<PGPPublicKey> publicKey = keyService.findPublicKey(pgpSignature.getKeyID());
                if (publicKey.isPresent()) {
                    PGPPublicKey pgpPublicKey = publicKey.get();
                    try {
                        boolean verified = SecuritySupport.verify(origin, pgpSignature, pgpPublicKey);
                        if (!verified) {
                            errors.put(key, error(pgpPublicKey, FAILED));
                        }
                        if (!trustedKeys.contains(key)) {
                            errors.put(key, error(pgpPublicKey, PASSED_NOT_TRUSTED));
                        }
                    } catch (PGPException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                } else {
                    errors.put(key, error(null, KEY_NOT_FOUND));
                }
            }
            if (errors.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new SignatureVerificationFailure(errors, keyService));
        }

        @Override
        public void stop() {
            keyService.close();
        }
    }
}
