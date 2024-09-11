/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.repositories.resolver.MetadataFetchingCost;
import org.gradle.api.internal.artifacts.repositories.transport.NetworkingIssueVerifier;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveState;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.resolve.ArtifactNotFoundException;
import org.gradle.internal.resolve.ArtifactResolveException;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;
import org.gradle.internal.resolve.result.ErroringResolveResult;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * A ModuleComponentRepository that catches any exception and applies it to the result object.
 * This allows other repository implementations to throw exceptions on failure.
 *
 * This implementation will also disable any repository that throws a critical failure, failing-fast with that
 * repository for any subsequent requests.
 */
public class ErrorHandlingModuleComponentRepository implements ModuleComponentRepository<ModuleComponentGraphResolveState> {

    private final ModuleComponentRepository<ModuleComponentGraphResolveState> delegate;
    private final ErrorHandlingModuleComponentRepositoryAccess local;
    private final ErrorHandlingModuleComponentRepositoryAccess remote;

    public ErrorHandlingModuleComponentRepository(ModuleComponentRepository<ModuleComponentGraphResolveState> delegate, RepositoryDisabler remoteRepositoryDisabler) {
        this.delegate = delegate;
        local = new ErrorHandlingModuleComponentRepositoryAccess(delegate.getLocalAccess(), getId(), RepositoryDisabler.NoOpDisabler.INSTANCE, getName());
        remote = new ErrorHandlingModuleComponentRepositoryAccess(delegate.getRemoteAccess(), getId(), remoteRepositoryDisabler, getName());
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public ModuleComponentRepositoryAccess<ModuleComponentGraphResolveState> getLocalAccess() {
        return local;
    }

    @Override
    public ModuleComponentRepositoryAccess<ModuleComponentGraphResolveState> getRemoteAccess() {
        return remote;
    }

    @Override
    public Map<ComponentArtifactIdentifier, ResolvableArtifact> getArtifactCache() {
        return delegate.getArtifactCache();
    }

    @Override
    public InstantiatingAction<ComponentMetadataSupplierDetails> getComponentMetadataSupplier() {
        return delegate.getComponentMetadataSupplier();
    }

    private static final class ErrorHandlingModuleComponentRepositoryAccess implements ModuleComponentRepositoryAccess<ModuleComponentGraphResolveState> {
        private static final Logger LOGGER = Logging.getLogger(ErrorHandlingModuleComponentRepositoryAccess.class);
        private final static String MAX_TENTATIVES_BEFORE_DISABLING = "org.gradle.internal.repository.max.tentatives";
        private final static String INITIAL_BACKOFF_MS = "org.gradle.internal.repository.initial.backoff";

        private final ModuleComponentRepositoryAccess<ModuleComponentGraphResolveState> delegate;
        private final String repositoryId;
        private final RepositoryDisabler repositoryDisabler;
        private final int maxTentativesCount;
        private final int initialBackOff;
        private final String repositoryName;

        private ErrorHandlingModuleComponentRepositoryAccess(ModuleComponentRepositoryAccess<ModuleComponentGraphResolveState> delegate, String repositoryId, RepositoryDisabler repositoryDisabler, String repositoryName) {
            this(delegate, repositoryId, repositoryDisabler, Integer.getInteger(MAX_TENTATIVES_BEFORE_DISABLING, 3), Integer.getInteger(INITIAL_BACKOFF_MS, 1000), repositoryName);
        }

        private ErrorHandlingModuleComponentRepositoryAccess(ModuleComponentRepositoryAccess<ModuleComponentGraphResolveState> delegate, String repositoryId, RepositoryDisabler repositoryDisabler, int maxTentativesCount, int initialBackoff, String repositoryName) {
            this.repositoryName = repositoryName;
            assert maxTentativesCount > 0 : "Max tentatives must be > 0";
            assert initialBackoff >= 0 : "Initial backoff must be >= 0";
            this.delegate = delegate;
            this.repositoryId = repositoryId;
            this.repositoryDisabler = repositoryDisabler;
            this.maxTentativesCount = maxTentativesCount;
            this.initialBackOff = initialBackoff;
        }

        @Override
        public String toString() {
            return "error handling > " + delegate.toString();
        }

        @Override
        public void listModuleVersions(ModuleDependencyMetadata dependency, BuildableModuleVersionListingResolveResult result) {
            performOperationWithRetries(result,
                () -> delegate.listModuleVersions(dependency, result),
                cause -> new ModuleVersionResolveException(dependency.getSelector(), () -> buildDisabledRepositoryErrorMessage(repositoryName)),
                cause -> {
                    ModuleComponentSelector selector = dependency.getSelector();
                    return new ModuleVersionResolveException(selector, () -> "Failed to list versions for " + selector.getGroup() + ":" + selector.getModule() + ".", cause);
                });
        }

        @Override
        public void resolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult<ModuleComponentGraphResolveState> result) {
            performOperationWithRetries(result,
                () -> delegate.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result),
                cause -> new ModuleVersionResolveException(moduleComponentIdentifier, () -> buildDisabledRepositoryErrorMessage(repositoryName), cause),
                cause -> new ModuleVersionResolveException(moduleComponentIdentifier, cause)
            );
        }

        @Override
        public void resolveArtifactsWithType(ComponentArtifactResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
            performOperationWithRetries(result,
                () -> delegate.resolveArtifactsWithType(component, artifactType, result),
                cause -> new ArtifactResolveException(component.getId(), buildDisabledRepositoryErrorMessage(repositoryName), cause),
                cause -> new ArtifactResolveException(component.getId(), cause)
            );
        }

        @Override
        public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSources moduleSources, BuildableArtifactFileResolveResult result) {
            performOperationWithRetries(result,
                () -> {
                    delegate.resolveArtifact(artifact, moduleSources, result);
                    if (result.hasResult()) {
                        ArtifactResolveException failure = result.getFailure();
                        if (!(failure instanceof ArtifactNotFoundException)) {
                            return failure;
                        }
                    }
                    return null;
                },
                cause -> new ArtifactResolveException(artifact.getId(), buildDisabledRepositoryErrorMessage(repositoryName), cause),
                cause -> new ArtifactResolveException(artifact.getId(), cause));
        }

        private static String buildDisabledRepositoryErrorMessage(String repositoryName) {
            return String.format("Repository %s is disabled due to earlier error below:", repositoryName);
        }

        private <E extends Throwable, R extends ErroringResolveResult<E>> void performOperationWithRetries(R result,
                                                                                                           Callable<E> operation,
                                                                                                           Transformer<E, Throwable> onDisabled,
                                                                                                           Transformer<E, Throwable> onError) {
            if (checkToHandleDisabledRepository(result, onDisabled)) {
                return;
            }
            tryResolveAndMaybeDisable(result, operation, onError);
        }

        private <E extends Throwable, R extends ErroringResolveResult<E>> void performOperationWithRetries(R result,
                                                                                                           Runnable operation,
                                                                                                           Transformer<E, Throwable> onDisabled,
                                                                                                           Transformer<E, Throwable> onError) {
            if (checkToHandleDisabledRepository(result, onDisabled)) {
                return;
            }
            tryResolveAndMaybeDisable(result, operation, onError);
        }

        private <E extends Throwable, R extends ErroringResolveResult<E>> boolean checkToHandleDisabledRepository(R result, Transformer<E, Throwable> onDisabled) {
            if (repositoryDisabler.isDisabled(repositoryId)) {
                Throwable reason = repositoryDisabler.getDisabledReason(repositoryId).get();
                E failure = onDisabled.transform(reason);
                result.failed(failure);
                return true;
            }
            return false;
        }

        private <E extends Throwable, R extends ErroringResolveResult<E>> void tryResolveAndMaybeDisable(R result,
                                                                                                         Runnable operation,
                                                                                                         Transformer<E, Throwable> onError) {
            tryResolveAndMaybeDisable(result, () -> {
                operation.run();
                return null;
            }, onError);
        }

        private <E extends Throwable, R extends ErroringResolveResult<E>> void tryResolveAndMaybeDisable(R result,
                                                                                                         Callable<E> operation,
                                                                                                         Transformer<E, Throwable> onError) {
            int retries = 0;
            int backoff = initialBackOff;

            while (retries < maxTentativesCount) {
                retries++;
                E failure;
                Throwable unexpectedFailure = null;
                try {
                    failure = operation.call();
                    if (failure == null) {
                        if (retries > 1) {
                            LOGGER.debug("Successfully fetched external resource after {} retries", retries - 1);
                        }
                        return;
                    }
                } catch (Exception throwable) {
                    unexpectedFailure = throwable;
                    failure = onError.transform(throwable);
                }
                boolean doNotRetry = NetworkingIssueVerifier.isLikelyPermanentNetworkIssue(failure) || !NetworkingIssueVerifier.isLikelyTransientNetworkingIssue(failure);
                if (doNotRetry || retries == maxTentativesCount) {
                    if (unexpectedFailure != null) {
                        repositoryDisabler.tryDisableRepository(repositoryId, failure);
                    }
                    result.failed(failure);
                    break;
                } else {
                    LOGGER.debug("Error while accessing remote repository {}. Waiting {}ms before next retry. {} retries left", repositoryName, backoff, maxTentativesCount - retries, failure);
                    try {
                        Thread.sleep(backoff);
                        backoff *= 2;
                    } catch (InterruptedException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }
            }
        }

        @Override
        public MetadataFetchingCost estimateMetadataFetchingCost(ModuleComponentIdentifier moduleComponentIdentifier) {
            return delegate.estimateMetadataFetchingCost(moduleComponentIdentifier);
        }
    }
}
