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
package org.gradle.api.internal.artifacts.verification;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.Action;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.verification.model.ArtifactVerificationMetadata;
import org.gradle.api.internal.artifacts.verification.model.Checksum;
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind;
import org.gradle.api.internal.artifacts.verification.model.ComponentVerificationMetadata;
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

    DependencyVerifier(Map<ComponentIdentifier, ComponentVerificationMetadata> verificationMetadata) {
        this.verificationMetadata = ImmutableMap.copyOf(verificationMetadata);
    }

    // This cache is a mapping from a file to its verification failure state
    // This avoids recomputing the same checksums multiple times, especially
    // when different subprojects use the same dependencies. It uses weak keys/values
    // because it's only intended as a performance optimization.
    private final Cache<File, Optional<VerificationFailure>> verificationCache = CacheBuilder.newBuilder()
        .weakKeys()
        .weakValues()
        .build();

    public void verify(BuildOperationExecutor buildOperationExecutor, ChecksumService checksumService, ModuleComponentArtifactIdentifier foundArtifact, File file, Action<VerificationFailure> onFailure) {
        try {
            Optional<VerificationFailure> verificationFailure = verificationCache.get(file, () -> {
                return performVerification(buildOperationExecutor, foundArtifact, checksumService, file);
            });
            verificationFailure.ifPresent(f -> onFailure.execute(f));
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private Optional<VerificationFailure> performVerification(BuildOperationExecutor buildOperationExecutor, ModuleComponentArtifactIdentifier foundArtifact, ChecksumService checksumService, File file) {
        return buildOperationExecutor.call(new CallableBuildOperation<Optional<VerificationFailure>>() {
            @Override
            public Optional<VerificationFailure> call(BuildOperationContext context) {
                if (!file.exists()) {
                    return VerificationFailure.OPT_DELETED;
                }
                return doVerifyArtifact(foundArtifact, checksumService, file);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Verifying dependency " + foundArtifact);
            }
        });
    }

    private Optional<VerificationFailure> doVerifyArtifact(ModuleComponentArtifactIdentifier foundArtifact, ChecksumService checksumService, File file) {
        AtomicReference<VerificationFailure> failure = new AtomicReference<>();
        ComponentVerificationMetadata componentVerification = verificationMetadata.get(foundArtifact.getComponentIdentifier());
        if (componentVerification != null) {
            String foundArtifactFileName = foundArtifact.getFileName();
            List<ArtifactVerificationMetadata> verifications = componentVerification.getArtifactVerifications();
            for (ArtifactVerificationMetadata verification : verifications) {
                String verifiedArtifact = verification.getArtifactName();
                if (verifiedArtifact.equals(foundArtifactFileName)) {
                    List<Checksum> checksums = verification.getChecksums();
                    for (Checksum checksum : checksums) {
                        verifyChecksum(checksum.getKind(), file, checksum.getValue(), checksum.getAlternatives(), checksumService, f -> failure.set(f));
                        if (failure.get() != null) {
                            return Optional.of(failure.get());
                        }
                    }
                    return Optional.empty();
                }
            }
        }

        return VerificationFailure.OPT_MISSING;
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
        onFailure.execute(new VerificationFailure(algorithm, expected, actualChecksum));
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

    public static class VerificationFailure {
        public static final VerificationFailure MISSING = new VerificationFailure(null, null, null);
        public static final VerificationFailure DELETED = new VerificationFailure(null, null, null);
        private static final Optional<VerificationFailure> OPT_MISSING = Optional.of(MISSING);
        private static final Optional<VerificationFailure> OPT_DELETED = Optional.of(DELETED);

        private final ChecksumKind kind;
        private final String expected;
        private final String actual;

        VerificationFailure(ChecksumKind kind, String expected, String actual) {
            this.kind = kind;
            this.expected = expected;
            this.actual = actual;
        }

        public ChecksumKind getKind() {
            return kind;
        }

        public String getExpected() {
            return expected;
        }

        public String getActual() {
            return actual;
        }
    }
}
