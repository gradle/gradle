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
import org.gradle.api.Action;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.ArtifactVerificationOperation;
import org.gradle.api.internal.artifacts.verification.model.ArtifactVerificationMetadata;
import org.gradle.api.internal.artifacts.verification.model.Checksum;
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind;
import org.gradle.api.internal.artifacts.verification.model.ComponentVerificationMetadata;
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationService;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

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

    public void verify(BuildOperationExecutor buildOperationExecutor, ChecksumService checksumService, SignatureVerificationService signatureVerificationService, ArtifactVerificationOperation.ArtifactKind kind, ModuleComponentArtifactIdentifier foundArtifact, File artifactFile, File signatureFile, Action<VerificationFailure> onFailure) {
        if (shouldSkipVerification(kind)) {
            return;
        }
        try {
            Optional<? extends VerificationFailure> verificationFailure = verificationCache.get(artifactFile, () -> {
                return performVerification(buildOperationExecutor, foundArtifact, checksumService, signatureVerificationService, artifactFile, signatureFile);
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

    private Optional<? extends VerificationFailure> performVerification(BuildOperationExecutor buildOperationExecutor, ModuleComponentArtifactIdentifier foundArtifact, ChecksumService checksumService, SignatureVerificationService signatureVerificationService, File file, File signature) {
        return buildOperationExecutor.call(new CallableBuildOperation<Optional<? extends VerificationFailure>>() {
            @Override
            public Optional<? extends VerificationFailure> call(BuildOperationContext context) {
                if (!file.exists()) {
                    return VerificationFailure.OPT_DELETED;
                }
                return doVerifyArtifact(foundArtifact, checksumService, signatureVerificationService, file, signature);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                String displayName = "Verifying dependency " + foundArtifact;
                return BuildOperationDescriptor.displayName(displayName)
                    .progressDisplayName(displayName);
            }
        });
    }

    private Optional<? extends VerificationFailure> doVerifyArtifact(ModuleComponentArtifactIdentifier foundArtifact, ChecksumService checksumService, SignatureVerificationService signatureVerificationService, File file, File signature) {
        AtomicReference<VerificationFailure> failure = new AtomicReference<>();
        ComponentVerificationMetadata componentVerification = verificationMetadata.get(foundArtifact.getComponentIdentifier());
        if (componentVerification != null) {
            String foundArtifactFileName = foundArtifact.getFileName();
            List<ArtifactVerificationMetadata> verifications = componentVerification.getArtifactVerifications();
            for (ArtifactVerificationMetadata verification : verifications) {
                String verifiedArtifact = verification.getArtifactName();
                if (verifiedArtifact.equals(foundArtifactFileName)) {
                    if (signature != null) {
                        Set<String> trustedKeys = verification.getTrustedPgpKeys();
                        Optional<SignatureVerificationFailure> verificationFailure = signatureVerificationService.verify(file, signature, trustedKeys);
                        if (verificationFailure.isPresent()) {
                            return verificationFailure;
                        }
                    }
                    return verifyChecksums(checksumService, file, failure, verification);
                }
            }
        }

        return VerificationFailure.OPT_MISSING;
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

}
