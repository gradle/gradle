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
import com.google.common.collect.Sets;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.artifacts.verification.DependencyVerificationMode;
import org.gradle.api.component.Artifact;
import org.gradle.api.internal.DocumentationRegistry;
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
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChecksumAndSignatureVerificationOverride implements DependencyVerificationOverride, ArtifactVerificationOperation {
    private final static Logger LOGGER = Logging.getLogger(ChecksumAndSignatureVerificationOverride.class);

    private static final Comparator<Map.Entry<ModuleComponentArtifactIdentifier, Collection<FailureWrapper>>> DELETED_LAST = Comparator.comparing(e -> e.getValue().stream().anyMatch(f -> f.failure instanceof DeletedArtifact) ? 1 : 0);
    private static final Comparator<Map.Entry<ModuleComponentArtifactIdentifier, Collection<FailureWrapper>>> MISSING_LAST = Comparator.comparing(e -> e.getValue().stream().anyMatch(f -> f.failure instanceof MissingChecksums) ? 1 : 0);
    private static final Comparator<Map.Entry<ModuleComponentArtifactIdentifier, Collection<FailureWrapper>>> BY_MODULE_ID = Comparator.comparing(e -> e.getKey().getDisplayName());

    private final DependencyVerifier verifier;
    private final Multimap<ModuleComponentArtifactIdentifier, FailureWrapper> failures = LinkedHashMultimap.create();
    private final BuildOperationExecutor buildOperationExecutor;
    private final Path gradleUserHome;
    private final ChecksumService checksumService;
    private final SignatureVerificationService signatureVerificationService;
    private final DependencyVerificationMode verificationMode;
    private final DocumentationRegistry documentationRegistry;
    private final Set<VerificationQuery> verificationQueries = Sets.newConcurrentHashSet();
    private final Deque<VerificationEvent> verificationEvents = Queues.newArrayDeque();

    public ChecksumAndSignatureVerificationOverride(BuildOperationExecutor buildOperationExecutor,
                                                    File gradleUserHome,
                                                    File verificationsFile,
                                                    File keyRingsFile,
                                                    ChecksumService checksumService,
                                                    SignatureVerificationServiceFactory signatureVerificationServiceFactory,
                                                    DependencyVerificationMode verificationMode,
                                                    DocumentationRegistry documentationRegistry) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.gradleUserHome = gradleUserHome.toPath();
        this.checksumService = checksumService;
        this.verificationMode = verificationMode;
        this.documentationRegistry = documentationRegistry;
        try {
            this.verifier = DependencyVerificationsXmlReader.readFromXml(
                new FileInputStream(verificationsFile)
            );
        } catch (FileNotFoundException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        this.signatureVerificationService = signatureVerificationServiceFactory.create(keyRingsFile, keyServers());
    }

    private List<URI> keyServers() {
        return DefaultKeyServers.getOrDefaults(verifier.getConfiguration().getKeyServers());
    }

    @Override
    public void onArtifact(ArtifactKind kind, ModuleComponentArtifactIdentifier artifact, File mainFile, Factory<File> signatureFile, String repositoryName, String repositoryId) {
        if (verificationQueries.add(new VerificationQuery(artifact, repositoryId))) {
            VerificationEvent event = new VerificationEvent(kind, artifact, mainFile, signatureFile, repositoryName);
            synchronized (verificationEvents) {
                verificationEvents.add(event);
            }
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
                                    failures.put(ve.artifact, new FailureWrapper(f, ve.repositoryName));
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
                Set<String> affectedFiles = Sets.newTreeSet();
                // Sorting entries so that error messages are always displayed in a reproducible order
                failures.asMap()
                    .entrySet()
                    .stream()
                    .sorted(DELETED_LAST.thenComparing(MISSING_LAST).thenComparing(BY_MODULE_ID))
                    .forEachOrdered(entry -> {
                        ModuleComponentArtifactIdentifier key = entry.getKey();
                        Collection<FailureWrapper> failures = entry.getValue();
                        if (failures.stream().anyMatch(f -> f.failure.isFatal())) {
                            failures.stream()
                                .map(FailureWrapper::getFailure)
                                .map(this::extractFailedFilePaths)
                                .forEach(affectedFiles::add);
                            hasFatalFailure.set(true);
                            formatter.node("On artifact " + key + " ");
                            if (failures.size() == 1) {
                                FailureWrapper firstFailure = failures.iterator().next();
                                explainSingleFailure(formatter, maybeCompromised, hasMissing, failedSignatures, firstFailure);
                            } else {
                                explainMultiFailure(formatter, maybeCompromised, hasMissing, failedSignatures, failures);
                            }
                        }
                    });
                formatter.endChildren();
                formatter.blankLine();
                if (maybeCompromised.get()) {
                    formatter.node("This can indicate that a dependency has been compromised. Please carefully verify the ");
                    if (failedSignatures.get()) {
                        formatter.append("signatures and ");
                    }
                    formatter.append("checksums.");
                } else if (hasMissing.get()) {
                    // the else is just to avoid telling people to use `--write-verification-metadata` if we suspect compromised dependencies
                    formatter.node("If the dependency is legit, follow the instructions at " + documentationRegistry.getDocumentationFor("dependency_verification", "sec:troubleshooting-verification"));
                }
                if (!affectedFiles.isEmpty()) {
                    formatter.blankLine();
                    formatter.node("For your information here are the files which failed verification:");
                    formatter.startChildren();
                    for (String affectedFile : affectedFiles) {
                        formatter.node(affectedFile);
                    }
                    formatter.endChildren();
                    formatter.blankLine();
                    formatter.node("GRADLE_USERHOME = " + gradleUserHome);
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

    private String extractFailedFilePaths(VerificationFailure f) {
        String shortenPath = shortenPath(f.getFilePath());
        if (f instanceof SignatureVerificationFailure) {
            File signatureFile = ((SignatureVerificationFailure) f).getSignatureFile();
            return shortenPath + " (signature: " + shortenPath(signatureFile) + ")";
        }
        return shortenPath;
    }

    // Shortens the path for display the user
    private String shortenPath(File file) {
        Path path = file.toPath();
        try {
            Path relativize = gradleUserHome.relativize(path);
            return "GRADLE_USERHOME" + File.separator + relativize;
        } catch (IllegalArgumentException e) {
            return file.getAbsolutePath();
        }
    }

    private void explainMultiFailure(TreeFormatter formatter, AtomicBoolean maybeCompromised, AtomicBoolean hasMissing, AtomicBoolean failedSignatures, Collection<FailureWrapper> failures) {
        formatter.append("multiple problems reported");
        formatter.startChildren();
        for (FailureWrapper failure : failures) {
            formatter.node("");
            explainSingleFailure(formatter, maybeCompromised, hasMissing, failedSignatures, failure);
        }
        formatter.endChildren();
    }

    private void explainSingleFailure(TreeFormatter formatter, AtomicBoolean maybeCompromised, AtomicBoolean hasMissing, AtomicBoolean failedSignatures, FailureWrapper wrapper) {
        VerificationFailure failure = wrapper.failure;
        if (failure instanceof MissingChecksums) {
            hasMissing.set(true);
        } else {
            if (failure instanceof SignatureVerificationFailure) {
                failedSignatures.set(true);
            }
            maybeCompromised.set(true);
        }
        formatter.append("in repository '" + wrapper.repositoryName + "': ");
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

    private static class FailureWrapper {
        private final VerificationFailure failure;
        private final String repositoryName;

        private FailureWrapper(VerificationFailure failure, String repositoryName) {
            this.failure = failure;
            this.repositoryName = repositoryName;
        }

        public VerificationFailure getFailure() {
            return failure;
        }
    }

    private static class VerificationQuery {
        private final ModuleComponentArtifactIdentifier artifact;
        private final String repositoryId;
        private final int hashCode;

        public VerificationQuery(ModuleComponentArtifactIdentifier artifact, String repositoryId) {
            this.artifact = artifact;
            this.repositoryId = repositoryId;
            this.hashCode = precomputeHashCode(artifact, repositoryId);
        }

        private int precomputeHashCode(ModuleComponentArtifactIdentifier artifact, String repositoryId) {
            int hashCode = artifact.getComponentIdentifier().hashCode();
            hashCode = 31 * hashCode + artifact.getFileName().hashCode();
            hashCode = 31 * hashCode + repositoryId.hashCode();
            return hashCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            VerificationQuery that = (VerificationQuery) o;
            if (hashCode != that.hashCode) {
                return false;
            }
            if (!artifact.getComponentIdentifier().equals(that.artifact.getComponentIdentifier())) {
                return false;
            }
            if (!artifact.getFileName().equals(that.artifact.getFileName())) {
                return false;
            }
            return repositoryId.equals(that.repositoryId);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private static class VerificationEvent {
        private final ArtifactKind kind;
        private final ModuleComponentArtifactIdentifier artifact;
        private final File mainFile;
        private final Factory<File> signatureFile;
        private final String repositoryName;

        private VerificationEvent(ArtifactKind kind, ModuleComponentArtifactIdentifier artifact, File mainFile, Factory<File> signatureFile, String repositoryName) {
            this.kind = kind;
            this.artifact = artifact;
            this.mainFile = mainFile;
            this.signatureFile = signatureFile;
            this.repositoryName = repositoryName;
        }
    }
}
