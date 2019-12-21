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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Queues;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.artifacts.verification.DependencyVerificationMode;
import org.gradle.api.component.Artifact;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DependencyVerifyingModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationsXmlReader;
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationService;
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationServiceFactory;
import org.gradle.api.internal.artifacts.verification.verifier.DeletedArtifact;
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifier;
import org.gradle.api.internal.artifacts.verification.verifier.MissingChecksums;
import org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure;
import org.gradle.api.internal.artifacts.verification.verifier.VerificationFailure;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChecksumAndSignatureVerificationOverride implements DependencyVerificationOverride, ArtifactVerificationOperation {
    private final static Logger LOGGER = Logging.getLogger(ChecksumAndSignatureVerificationOverride.class);

    private static final Comparator<Map.Entry<ModuleComponentArtifactIdentifier, Collection<VerificationFailure>>> DELETED_LAST = Comparator.comparing(e -> e.getValue().stream().anyMatch(f -> f == DeletedArtifact.INSTANCE) ? 1 : 0);
    private static final Comparator<Map.Entry<ModuleComponentArtifactIdentifier, Collection<VerificationFailure>>> MISSING_LAST = Comparator.comparing(e -> e.getValue().stream().anyMatch(f -> f == MissingChecksums.INSTANCE) ? 1 : 0);
    private static final Comparator<Map.Entry<ModuleComponentArtifactIdentifier, Collection<VerificationFailure>>> BY_MODULE_ID = Comparator.comparing(e -> e.getKey().getDisplayName());

    private final DependencyVerifier verifier;
    private final Multimap<ModuleComponentArtifactIdentifier, VerificationFailure> failures = LinkedHashMultimap.create();
    private final BuildOperationExecutor buildOperationExecutor;
    private final ChecksumService checksumService;
    private final SignatureVerificationService signatureVerificationService;
    private final DependencyVerificationMode verificationMode;
    private final Deque<VerificationEvent> verificationEvents = Queues.newArrayDeque();

    public ChecksumAndSignatureVerificationOverride(BuildOperationExecutor buildOperationExecutor, File verificationsFile, ChecksumService checksumService, SignatureVerificationServiceFactory signatureVerificationServiceFactory, DependencyVerificationMode verificationMode) {
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
        this.signatureVerificationService = signatureVerificationServiceFactory.create(keyServers());
    }

    private List<URI> keyServers() {
        return DefaultKeyServers.getOrDefaults(verifier.getConfiguration().getKeyServers());
    }

    @Override
    public void onArtifact(ArtifactKind kind, ModuleComponentArtifactIdentifier artifact, File mainFile, Factory<File> signatureFile) {
        VerificationEvent event = new VerificationEvent(kind, artifact, mainFile, signatureFile);
        synchronized (verificationEvents) {
            verificationEvents.add(event);
        }
    }

    private void verifyConcurrently() {
        synchronized (verificationEvents) {
            if (verificationEvents.isEmpty()) {
                return;
            }
        }
        buildOperationExecutor.runAll(queue -> {
            VerificationEvent event;
            synchronized (verificationEvents) {
                while ((event = verificationEvents.poll()) != null) {
                    VerificationEvent ve = event;
                    queue.add(new RunnableBuildOperation() {
                        @Override
                        public void run(BuildOperationContext context) {
                            verifier.verify(checksumService, signatureVerificationService, ve.kind, ve.artifact, ve.mainFile, ve.signatureFile.create(), f -> {
                                synchronized (failures) {
                                    failures.put(ve.artifact, f);
                                }
                            });
                        }

                        @Override
                        public BuildOperationDescriptor.Builder description() {
                            return BuildOperationDescriptor.displayName("Dependency verification")
                                .progressDisplayName("Verifying " + ve.artifact);
                        }
                    });
                }
            }
        });

    }

    @Override
    public ModuleComponentRepository overrideDependencyVerification(ModuleComponentRepository original) {
        return new DependencyVerifyingModuleComponentRepository(original, this, verifier.getConfiguration().isVerifySignatures());
    }

    @Override
    public void artifactsAccessed(String displayName) {
        verifyConcurrently();
        synchronized (failures) {
            if (!failures.isEmpty()) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("Dependency verification failed for " + displayName);
                formatter.startChildren();
                AtomicBoolean maybeCompromised = new AtomicBoolean();
                AtomicBoolean hasMissing = new AtomicBoolean();
                AtomicBoolean failedSignatures = new AtomicBoolean();
                AtomicBoolean hasFatalFailure = new AtomicBoolean();
                // Sorting entries so that error messages are always displayed in a reproducible order
                failures.asMap()
                    .entrySet()
                    .stream()
                    .sorted(DELETED_LAST.thenComparing(MISSING_LAST).thenComparing(BY_MODULE_ID))
                    .forEachOrdered(entry -> {
                        ModuleComponentArtifactIdentifier key = entry.getKey();
                        Collection<VerificationFailure> failures = entry.getValue();
                        if (failures.stream().anyMatch(VerificationFailure::isFatal)) {
                            hasFatalFailure.set(true);
                            formatter.node("On artifact " + key + ": ");
                            if (failures.size() == 1) {
                                explainSingleFailure(formatter, maybeCompromised, hasMissing, failedSignatures, failures.iterator().next());
                            } else {
                                explainMultiFailure(formatter, maybeCompromised, hasMissing, failedSignatures, failures);
                            }
                        }
                    });
                formatter.endChildren();
                if (maybeCompromised.get()) {
                    formatter.node("This can indicate that a dependency has been compromised. Please carefully verify the ");
                    if (failedSignatures.get()) {
                        formatter.append("signatures and ");
                    }
                    formatter.append("checksums.");
                } else if (hasMissing.get()) {
                    // the else is just to avoid telling people to use `--write-verification-metadata` if we suspect compromised dependencies
                    formatter.node("If the dependency is legit, update the gradle/dependency-verification.xml manually (safest) or run with the --write-verification-metadata flag (unsecure).");
                }
                if (hasFatalFailure.get()) {
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
    }

    private void explainMultiFailure(TreeFormatter formatter, AtomicBoolean maybeCompromised, AtomicBoolean hasMissing, AtomicBoolean failedSignatures, Collection<VerificationFailure> failures) {
        formatter.append("multiple problems reported");
        formatter.startChildren();
        for (VerificationFailure failure : failures) {
            formatter.node("");
            explainSingleFailure(formatter, maybeCompromised, hasMissing, failedSignatures, failure);
        }
        formatter.endChildren();
    }

    private void explainSingleFailure(TreeFormatter formatter, AtomicBoolean maybeCompromised, AtomicBoolean hasMissing, AtomicBoolean failedSignatures, VerificationFailure failure) {
        if (failure == MissingChecksums.INSTANCE) {
            hasMissing.set(true);
        } else {
            if (failure instanceof SignatureVerificationFailure) {
                failedSignatures.set(true);
            }
            maybeCompromised.set(true);
        }
        failure.explainTo(formatter);
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

    @Override
    public void buildFinished(Gradle gradle) {
        signatureVerificationService.stop();
    }

    private static class VerificationEvent {
        private final ArtifactKind kind;
        private final ModuleComponentArtifactIdentifier artifact;
        private final File mainFile;
        private final Factory<File> signatureFile;

        private VerificationEvent(ArtifactKind kind, ModuleComponentArtifactIdentifier artifact, File mainFile, Factory<File> signatureFile) {
            this.kind = kind;
            this.artifact = artifact;
            this.mainFile = mainFile;
            this.signatureFile = signatureFile;
        }
    }
}
