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
package org.gradle.api.internal.artifacts.verification.verifier;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.gradle.api.Action;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.ArtifactVerificationOperation;
import org.gradle.api.internal.artifacts.verification.model.ArtifactVerificationMetadata;
import org.gradle.api.internal.artifacts.verification.model.Checksum;
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind;
import org.gradle.api.internal.artifacts.verification.model.ComponentVerificationMetadata;
import org.gradle.api.internal.artifacts.verification.model.IgnoredKey;
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationResultBuilder;
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationService;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.hash.HashCode;
import org.gradle.security.internal.PublicKeyService;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.gradle.security.internal.SecuritySupport.toHexString;

public class DependencyVerifier {
    private final Map<ComponentIdentifier, ComponentVerificationMetadata> verificationMetadata;
    private final DependencyVerificationConfiguration config;

    DependencyVerifier(Map<ComponentIdentifier, ComponentVerificationMetadata> verificationMetadata, DependencyVerificationConfiguration config) {
        this.verificationMetadata = ImmutableMap.copyOf(verificationMetadata);
        this.config = config;
    }

    // This cache is a mapping from a file to its verification failure state
    // This avoids recomputing the same checksums multiple times, especially
    // when different subprojects use the same dependencies. It uses weak keys/values
    // because it's only intended as a performance optimization.
    private final Cache<File, Optional<? extends VerificationFailure>> verificationCache = CacheBuilder.newBuilder()
        .weakKeys()
        .weakValues()
        .build();

    public void verify(ChecksumService checksumService,
                       SignatureVerificationService signatureVerificationService,
                       ArtifactVerificationOperation.ArtifactKind kind,
                       ModuleComponentArtifactIdentifier foundArtifact,
                       File artifactFile,
                       File signatureFile,
                       Action<VerificationFailure> onFailure) {
        if (shouldSkipVerification(kind)) {
            return;
        }
        try {
            Optional<? extends VerificationFailure> verificationFailure = verificationCache.get(artifactFile, () -> {
                return performVerification(foundArtifact, checksumService, signatureVerificationService, artifactFile, signatureFile);
            });
            verificationFailure.ifPresent(f -> {
                // In order to avoid going through the list for every artifact, we only
                // check if it's trusted if an error is thrown
                if (isTrustedArtifact(foundArtifact)) {
                    return;
                }
                onFailure.execute(f);
            });
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private boolean shouldSkipVerification(ArtifactVerificationOperation.ArtifactKind kind) {
        if (kind == ArtifactVerificationOperation.ArtifactKind.METADATA && !config.isVerifyMetadata()) {
            return true;
        }
        return false;
    }

    private boolean isTrustedArtifact(ModuleComponentArtifactIdentifier id) {
        if (config.getTrustedArtifacts().stream().anyMatch(artifact -> artifact.matches(id))) {
            return true;
        }
        return false;
    }

    private Optional<? extends VerificationFailure> performVerification(ModuleComponentArtifactIdentifier foundArtifact, ChecksumService checksumService, SignatureVerificationService signatureVerificationService, File file, File signature) {
        if (!file.exists()) {
            return VerificationFailure.OPT_DELETED;
        }
        return doVerifyArtifact(foundArtifact, checksumService, signatureVerificationService, file, signature);
    }

    private Optional<? extends VerificationFailure> doVerifyArtifact(ModuleComponentArtifactIdentifier foundArtifact, ChecksumService checksumService, SignatureVerificationService signatureVerificationService, File file, File signature) {
        AtomicReference<VerificationFailure> failure = new AtomicReference<>();
        PublicKeyService publicKeyService = signatureVerificationService.getPublicKeyService();
        ComponentVerificationMetadata componentVerification = verificationMetadata.get(foundArtifact.getComponentIdentifier());
        if (componentVerification != null) {
            String foundArtifactFileName = foundArtifact.getFileName();
            List<ArtifactVerificationMetadata> verifications = componentVerification.getArtifactVerifications();
            for (ArtifactVerificationMetadata verification : verifications) {
                String verifiedArtifact = verification.getArtifactName();
                if (verifiedArtifact.equals(foundArtifactFileName)) {
                    if (signature != null) {
                        DefaultSignatureVerificationResultBuilder result = new DefaultSignatureVerificationResultBuilder();
                        verifySignature(signatureVerificationService, file, signature, allTrustedKeys(foundArtifact, verification.getTrustedPgpKeys()), allIgnoredKeys(verification.getIgnoredPgpKeys()), result);
                        if (result.hasOnlyIgnoredKeys()) {
                            if (verification.getChecksums().isEmpty()) {
                                return VerificationFailure.OPT_MISSING;
                            } else {
                                return verifyChecksums(checksumService, file, failure, verification);
                            }
                        }
                        if (result.hasError()) {
                            return Optional.of(result.asError(publicKeyService));
                        }
                        return Optional.empty();
                    }
                    return verifyChecksums(checksumService, file, failure, verification);
                }
            }
        }
        if (signature != null) {
            // it's possible that the artifact is not listed explicitly but we can still verify signatures
            DefaultSignatureVerificationResultBuilder result = new DefaultSignatureVerificationResultBuilder();
            verifySignature(signatureVerificationService, file, signature, allTrustedKeys(foundArtifact, Collections.emptySet()), allIgnoredKeys(Collections.emptySet()), result);
            if (result.hasError()) {
                return Optional.of(result.asError(publicKeyService));
            } else if (!result.hasOnlyIgnoredKeys()) {
                return Optional.empty();
            }
        }

        return VerificationFailure.OPT_MISSING;
    }

    private Set<String> allTrustedKeys(ModuleComponentArtifactIdentifier id, Set<String> artifactSpecificKeys) {
        if (config.getTrustedKeys().isEmpty()) {
            return artifactSpecificKeys;
        } else {
            Set<String> allKeys = Sets.newHashSet(artifactSpecificKeys);
            config.getTrustedKeys()
                .stream()
                .filter(trustedKey -> trustedKey.matches(id))
                .forEach(trustedKey -> allKeys.add(trustedKey.getKeyId()));
            return allKeys;
        }
    }

    private Set<String> allIgnoredKeys(Set<IgnoredKey> artifactSpecificKeys) {
        if (config.getIgnoredKeys().isEmpty()) {
            return artifactSpecificKeys.stream().map(IgnoredKey::getKeyId).collect(Collectors.toSet());
        } else {
            if (artifactSpecificKeys.isEmpty()) {
                return config.getIgnoredKeys().stream().map(IgnoredKey::getKeyId).collect(Collectors.toSet());
            }
            Set<String> allKeys = Sets.newHashSet();
            artifactSpecificKeys.stream()
                .map(IgnoredKey::getKeyId)
                .forEach(allKeys::add);
            config.getIgnoredKeys()
                .stream()
                .map(IgnoredKey::getKeyId)
                .forEach(allKeys::add);
            return allKeys;
        }
    }

    private void verifySignature(SignatureVerificationService signatureVerificationService, File file, File signature, Set<String> trustedKeys, Set<String> ignoredKeys, SignatureVerificationResultBuilder result) {
        signatureVerificationService.verify(file, signature, trustedKeys, ignoredKeys, result);
    }

    private Optional<VerificationFailure> verifyChecksums(ChecksumService checksumService, File file, AtomicReference<VerificationFailure> failure, ArtifactVerificationMetadata verification) {
        List<Checksum> checksums = verification.getChecksums();
        for (Checksum checksum : checksums) {
            verifyChecksum(checksum.getKind(), file, checksum.getValue(), checksum.getAlternatives(), checksumService, f -> failure.set(f));
            if (failure.get() != null) {
                return Optional.of(failure.get());
            }
        }
        return Optional.empty();
    }

    private static void verifyChecksum(ChecksumKind algorithm, File file, String expected, Set<String> alternatives, ChecksumService cache, Action<VerificationFailure> onFailure) {
        String actualChecksum = checksumOf(algorithm, file, cache);
        if (expected.equals(actualChecksum)) {
            return;
        }
        if (alternatives != null) {
            for (String alternative : alternatives) {
                if (actualChecksum.equals(alternative)) {
                    return;
                }
            }
        }
        onFailure.execute(new ChecksumVerificationFailure(algorithm, expected, actualChecksum));
    }

    private static String checksumOf(ChecksumKind algorithm, File file, ChecksumService cache) {
        HashCode hashValue = null;
        switch (algorithm) {
            case md5:
                hashValue = cache.md5(file);
                break;
            case sha1:
                hashValue = cache.sha1(file);
                break;
            case sha256:
                hashValue = cache.sha256(file);
                break;
            case sha512:
                hashValue = cache.sha512(file);
                break;
        }
        return hashValue.toString();
    }

    public Collection<ComponentVerificationMetadata> getVerificationMetadata() {
        return verificationMetadata.values();
    }

    public DependencyVerificationConfiguration getConfiguration() {
        return config;
    }

    private static class DefaultSignatureVerificationResultBuilder implements SignatureVerificationResultBuilder {
        private List<String> missingKeys = null;
        private List<PGPPublicKey> trustedKeys = null;
        private List<PGPPublicKey> validNotTrusted = null;
        private List<PGPPublicKey> failedKeys = null;
        private List<String> ignoredKeys = null;

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
                if (validNotTrusted == null) {
                    validNotTrusted = Lists.newArrayList();
                }
                validNotTrusted.add(key);
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

        boolean hasOnlyIgnoredKeys() {
            return ignoredKeys != null
                && trustedKeys == null
                && validNotTrusted == null
                && missingKeys == null
                && failedKeys == null;
        }

        public SignatureVerificationFailure asError(PublicKeyService publicKeyService) {
            ImmutableMap.Builder<String, SignatureVerificationFailure.SignatureError> errors = ImmutableMap.builder();
            if (missingKeys != null) {
                for (String missingKey : missingKeys) {
                    errors.put(missingKey, error(null, SignatureVerificationFailure.FailureKind.MISSING_KEY));
                }
            }
            if (failedKeys != null) {
                for (PGPPublicKey failedKey : failedKeys) {
                    errors.put(toHexString(failedKey.getKeyID()), error(failedKey, SignatureVerificationFailure.FailureKind.FAILED));
                }
            }
            if (validNotTrusted != null) {
                for (PGPPublicKey trustedKey : validNotTrusted) {
                    errors.put(toHexString(trustedKey.getKeyID()), error(trustedKey, SignatureVerificationFailure.FailureKind.PASSED_NOT_TRUSTED));
                }
            }
            if (ignoredKeys != null) {
                for (String ignoredKey : ignoredKeys) {
                    errors.put(ignoredKey, error(null, SignatureVerificationFailure.FailureKind.IGNORED_KEY));
                }
            }
            return new SignatureVerificationFailure(errors.build(), publicKeyService);
        }

        public boolean hasError() {
            return failedKeys != null || validNotTrusted != null || missingKeys != null;
        }
    }

    private static SignatureVerificationFailure.SignatureError error(@Nullable PGPPublicKey key, SignatureVerificationFailure.FailureKind kind) {
        return new SignatureVerificationFailure.SignatureError(key, kind);
    }
}
