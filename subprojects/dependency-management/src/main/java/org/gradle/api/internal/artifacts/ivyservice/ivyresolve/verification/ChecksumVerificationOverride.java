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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification;

import com.google.common.collect.Maps;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.artifacts.verification.DependencyVerificationMode;
import org.gradle.api.component.Artifact;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DependencyVerifyingModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.api.internal.artifacts.verification.DependencyVerifier;
import org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationsXmlReader;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.operations.BuildOperationExecutor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChecksumVerificationOverride implements DependencyVerificationOverride, ArtifactVerificationOperation {
    private final static Logger LOGGER = Logging.getLogger(ChecksumVerificationOverride.class);

    private static final Comparator<Map.Entry<ModuleComponentArtifactIdentifier, DependencyVerifier.VerificationFailure>> DELETED_LAST = Comparator.comparing(e -> e.getValue() == DependencyVerifier.VerificationFailure.DELETED ? 1 : 0);
    private static final Comparator<Map.Entry<ModuleComponentArtifactIdentifier, DependencyVerifier.VerificationFailure>> MISSING_LAST = Comparator.comparing(e -> e.getValue() == DependencyVerifier.VerificationFailure.MISSING ? 1 : 0);
    private static final Comparator<Map.Entry<ModuleComponentArtifactIdentifier, DependencyVerifier.VerificationFailure>> BY_MODULE_ID = Comparator.comparing(e -> e.getKey().getDisplayName());

    private final DependencyVerifier verifier;
    private final Map<ModuleComponentArtifactIdentifier, DependencyVerifier.VerificationFailure> failures = Maps.newLinkedHashMapWithExpectedSize(2);
    private final BuildOperationExecutor buildOperationExecutor;
    private final ChecksumService checksumService;
    private final DependencyVerificationMode verificationMode;

    public ChecksumVerificationOverride(BuildOperationExecutor buildOperationExecutor, File verificationsFile, ChecksumService checksumService, DependencyVerificationMode verificationMode) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.checksumService = checksumService;
        this.verificationMode = verificationMode;
        try {
            this.verifier = DependencyVerificationsXmlReader.readFromXml(
                new FileInputStream(verificationsFile)
            );
        } catch (FileNotFoundException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void onArtifact(ArtifactKind kind, ModuleComponentArtifactIdentifier artifact, File path) {
        verifier.verify(buildOperationExecutor, checksumService, kind, artifact, path, f -> {
            synchronized (failures) {
                failures.put(artifact, f);
            }
        });
    }

    @Override
    public ModuleComponentRepository overrideDependencyVerification(ModuleComponentRepository original) {
        return new DependencyVerifyingModuleComponentRepository(original, this);
    }

    @Override
    public void artifactsAccessed(String displayName) {
        synchronized (failures) {
            if (!failures.isEmpty()) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("Dependency verification failed for " + displayName);
                formatter.startChildren();
                AtomicBoolean maybeCompromised = new AtomicBoolean();
                AtomicBoolean hasMissing = new AtomicBoolean();
                // Sorting entries so that error messages are always displayed in a reproducible order
                failures.entrySet()
                    .stream()
                    .sorted(DELETED_LAST.thenComparing(MISSING_LAST).thenComparing(BY_MODULE_ID))
                    .forEachOrdered(entry -> {
                        DependencyVerifier.VerificationFailure failure = entry.getValue();
                        if (failure == DependencyVerifier.VerificationFailure.DELETED) {
                            formatter.node("Artifact " + entry.getKey() + " has been deleted from local cache so verification cannot be performed");
                        } else if (failure == DependencyVerifier.VerificationFailure.MISSING) {
                            hasMissing.set(true);
                            formatter.node("Artifact " + entry.getKey() + " checksum is missing from verification metadata.");
                        } else {
                            maybeCompromised.set(true);
                            formatter.node("On artifact " + entry.getKey() + ": expected a '" + failure.getKind() + "' checksum of '" + failure.getExpected() + "' but was '" + failure.getActual() + "'");
                        }
                    });
                formatter.endChildren();
                if (maybeCompromised.get()) {
                    formatter.node("This can indicate that a dependency has been compromised. Please verify carefully the checksums.");
                } else if (hasMissing.get()) {
                    // the else is just to avoid telling people to use `--write-verification-metadata` if we suspect compromised dependencies
                    formatter.node("Please update the file either manually (preferred) or by adding the --write-verification-metadata flag (unsafe).");
                }
                String message = formatter.toString();
                if (verificationMode == DependencyVerificationMode.LENIENT) {
                    LOGGER.error(message);
                    failures.clear();
                } else {
                    throw new InvalidUserDataException(message);
                }
            }
        }
    }

    @Override
    public ResolvedArtifactResult verifiedArtifact(ResolvedArtifactResult artifact) {
        return new ResolvedArtifactResult() {
            @Override
            public File getFile() {
                artifactsAccessed(artifact.getVariant().getDisplayName());
                return artifact.getFile();
            }

            @Override
            public ResolvedVariantResult getVariant() {
                return artifact.getVariant();
            }

            @Override
            public ComponentArtifactIdentifier getId() {
                return artifact.getId();
            }

            @Override
            public Class<? extends Artifact> getType() {
                return artifact.getType();
            }
        };
    }
}
