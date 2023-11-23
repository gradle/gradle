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
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.artifacts.verification.DependencyVerificationMode;
import org.gradle.api.component.Artifact;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DependencyVerifyingModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.report.DependencyVerificationReportWriter;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.report.VerificationReport;
import org.gradle.api.internal.artifacts.verification.exceptions.DependencyVerificationException;
import org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationsXmlReader;
import org.gradle.api.internal.artifacts.verification.signatures.BuildTreeDefinedKeys;
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationService;
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationServiceFactory;
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifier;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveState;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.resource.local.FileResourceListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChecksumAndSignatureVerificationOverride implements DependencyVerificationOverride, ArtifactVerificationOperation, Stoppable {
    private final static Logger LOGGER = Logging.getLogger(ChecksumAndSignatureVerificationOverride.class);

    private final DependencyVerifier verifier;
    private final Multimap<ModuleComponentArtifactIdentifier, RepositoryAwareVerificationFailure> failures = LinkedHashMultimap.create();
    private final BuildOperationExecutor buildOperationExecutor;
    private final ChecksumService checksumService;
    private final SignatureVerificationService signatureVerificationService;
    private final DependencyVerificationMode verificationMode;
    private final FileResourceListener fileResourceListener;
    private final Set<VerificationQuery> verificationQueries = Sets.newConcurrentHashSet();
    private final Deque<VerificationEvent> verificationEvents = Queues.newArrayDeque();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean hasFatalFailure = new AtomicBoolean();
    private final DependencyVerificationReportWriter reportWriter;

    public ChecksumAndSignatureVerificationOverride(
        BuildOperationExecutor buildOperationExecutor,
        File gradleUserHome,
        File verificationsFile,
        ChecksumService checksumService,
        SignatureVerificationServiceFactory signatureVerificationServiceFactory,
        DependencyVerificationMode verificationMode,
        DocumentationRegistry documentationRegistry,
        File reportsDirectory,
        Factory<GradleProperties> gradlePropertiesFactory,
        FileResourceListener fileResourceListener
    ) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.checksumService = checksumService;
        this.verificationMode = verificationMode;
        this.fileResourceListener = fileResourceListener;
        try {
            this.verifier = DependencyVerificationsXmlReader.readFromXml(
                new FileInputStream(observed(verificationsFile))
            );
            this.reportWriter = new DependencyVerificationReportWriter(gradleUserHome.toPath(), documentationRegistry, verificationsFile, verifier.getSuggestedWriteFlags(), reportsDirectory, gradlePropertiesFactory);
        } catch (FileNotFoundException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (DependencyVerificationException e) {
            throw new DependencyVerificationException("Unable to read dependency verification metadata from " + verificationsFile, e.getCause());
        }
        BuildTreeDefinedKeys localKeyring = new BuildTreeDefinedKeys(verificationsFile.getParentFile(), verifier.getConfiguration().getKeyringFormat());
        this.signatureVerificationService = signatureVerificationServiceFactory.create(localKeyring, keyServers(), verifier.getConfiguration().isUseKeyServers());
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
        hasFatalFailure.set(false);
        synchronized (verificationEvents) {
            if (verificationEvents.isEmpty()) {
                return;
            }
        }
        if (closed.get()) {
            LOGGER.debug("Cannot perform verification of all dependencies because the verification service has been shutdown. Under normal circumstances this shouldn't happen unless a user buildFinished was added in an unexpected way.");
            return;
        }
        buildOperationExecutor.runAll(queue -> {
            VerificationEvent event;
            synchronized (verificationEvents) {
                while ((event = verificationEvents.poll()) != null) {
                    VerificationEvent ve = event;
                    queue.add(new RunnableBuildOperation() {
                        @Override
                        public void run(BuildOperationContext context) {
                            verifier.verify(checksumService, signatureVerificationService, ve.kind, ve.artifact, observed(ve.mainFile), observed(ve.signatureFile.create()), f -> {
                                synchronized (failures) {
                                    failures.put(ve.artifact, new RepositoryAwareVerificationFailure(f, ve.repositoryName));
                                }
                                if (f.isFatal()) {
                                    hasFatalFailure.set(true);
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
    public ModuleComponentRepository<ModuleComponentGraphResolveState> overrideDependencyVerification(ModuleComponentRepository<ModuleComponentGraphResolveState> original, String resolveContextName, ResolutionStrategyInternal resolutionStrategy) {
        return new DependencyVerifyingModuleComponentRepository(original, this, verifier.getConfiguration().isVerifySignatures());
    }

    @Override
    public void artifactsAccessed(String displayName) {
        verifyConcurrently();
        synchronized (failures) {
            if (hasFatalFailure.get() && !failures.isEmpty()) {
                // There are fatal failures, but not necessarily on all artifacts so we first filter out
                // the artifacts which only have not fatal errors
                failures.asMap().entrySet().removeIf(entry -> {
                    Collection<RepositoryAwareVerificationFailure> value = entry.getValue();
                    return value.stream().noneMatch(wrapper -> wrapper.getFailure().isFatal());
                });
                VerificationReport report = reportWriter.generateReport(displayName, failures, verifier.getConfiguration().isUseKeyServers());
                String errorMessage = buildConsoleErrorMessage(report);
                if (verificationMode == DependencyVerificationMode.LENIENT) {
                    LOGGER.error(errorMessage);
                    failures.clear();
                    hasFatalFailure.set(false);
                } else {
                    throw new DependencyVerificationException(errorMessage);
                }
            }
        }
    }

    public String buildConsoleErrorMessage(VerificationReport report) {
        String errorMessage = report.getSummary();
        String htmlReport = new ConsoleRenderer().asClickableFileUrl(report.getHtmlReport());
        errorMessage += "\n\nOpen this report for more details: " + htmlReport;
        return errorMessage;
    }

    @Override
    public ResolvedArtifactResult verifiedArtifact(ResolvedArtifactResult artifact) {
        return new ResolvedArtifactResult() {
            @Override
            @SuppressWarnings("deprecation")
            public File getFile() {
                artifactsAccessed(artifact.getVariant().getDisplayName());
                return artifact.getFile();
            }

            @Override
            @Deprecated
            public ResolvedVariantResult getVariant() {
                return artifact.getVariant();
            }

            @Override
            public Set<ResolvedVariantResult> getVariants() {
                return artifact.getVariants();
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

    private File observed(File file) {
        if (file == null) {
            return file;
        }
        fileResourceListener.fileObserved(file);
        return file;
    }

    @Override
    public void stop() {
        closed.set(true);
        signatureVerificationService.stop();
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
