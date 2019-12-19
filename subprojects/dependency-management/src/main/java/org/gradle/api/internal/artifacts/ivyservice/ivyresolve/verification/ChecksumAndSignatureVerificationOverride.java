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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
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
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifier;
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
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChecksumAndSignatureVerificationOverride implements DependencyVerificationOverride, ArtifactVerificationOperation {
    private final static Logger LOGGER = Logging.getLogger(ChecksumAndSignatureVerificationOverride.class);

    private static final Comparator<Map.Entry<ModuleComponentArtifactIdentifier, VerificationFailure>> DELETED_LAST = Comparator.comparing(e -> e.getValue() == VerificationFailure.DELETED ? 1 : 0);
    private static final Comparator<Map.Entry<ModuleComponentArtifactIdentifier, VerificationFailure>> MISSING_LAST = Comparator.comparing(e -> e.getValue() == VerificationFailure.MISSING ? 1 : 0);
    private static final Comparator<Map.Entry<ModuleComponentArtifactIdentifier, VerificationFailure>> BY_MODULE_ID = Comparator.comparing(e -> e.getKey().getDisplayName());
    private static final ImmutableList<URI> DEFAULT_KEYSERVERS = ImmutableList.of(
        uri("https://pgp.key-server.io"),
        uri("hkp://pool.sks-keyservers.net"),
        uri("https://keys.fedoraproject.org"),
        uri("https://keyserver.ubuntu.com"),
        uri("hkp://keys.openpgp.org")
    );

    private final DependencyVerifier verifier;
    private final Map<ModuleComponentArtifactIdentifier, VerificationFailure> failures = Maps.newLinkedHashMapWithExpectedSize(2);
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
        return verifier.getConfiguration().getKeyServers().isEmpty() ? DEFAULT_KEYSERVERS : verifier.getConfiguration().getKeyServers();
    }

    private static URI uri(String uri) {
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
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
                // Sorting entries so that error messages are always displayed in a reproducible order
                failures.entrySet()
                    .stream()
                    .sorted(DELETED_LAST.thenComparing(MISSING_LAST).thenComparing(BY_MODULE_ID))
                    .forEachOrdered(entry -> {
                        VerificationFailure failure = entry.getValue();
                        if (failure == VerificationFailure.DELETED) {
                            formatter.node("Artifact " + entry.getKey() + " has been deleted from local cache so verification cannot be performed");
                        } else if (failure == VerificationFailure.MISSING) {
                            hasMissing.set(true);
                            formatter.node("Artifact " + entry.getKey() + " checksum is missing from verification metadata.");
                        } else {
                            maybeCompromised.set(true);
                            formatter.node("On artifact " + entry.getKey() + ": ");
                            failure.explainTo(formatter);
                        }
                    });
                formatter.endChildren();
                if (maybeCompromised.get()) {
                    formatter.node("This can indicate that a dependency has been compromised. Please verify carefully the checksums.");
                } else if (hasMissing.get()) {
                    // the else is just to avoid telling people to use `--write-verification-metadata` if we suspect compromised dependencies
                    formatter.node("If the dependency is legit, update the gradle/dependency-verification.xml manually (safest) or run with the --write-verification-metadata flag (unsecure).");
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
