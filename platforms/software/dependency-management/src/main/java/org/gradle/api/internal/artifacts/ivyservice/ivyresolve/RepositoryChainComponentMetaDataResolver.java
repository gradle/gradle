/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.repositories.resolver.MetadataFetchingCost;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static org.gradle.internal.resolve.ResolveExceptionAnalyzer.hasCriticalFailure;
import static org.gradle.internal.resolve.ResolveExceptionAnalyzer.isCriticalFailure;

public class RepositoryChainComponentMetaDataResolver implements ComponentMetaDataResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryChainComponentMetaDataResolver.class);

    private final List<ModuleComponentRepository<ModuleComponentGraphResolveState>> repositories = new ArrayList<>();
    private final List<String> repositoryNames = new ArrayList<>();
    private final VersionedComponentChooser versionedComponentChooser;

    public RepositoryChainComponentMetaDataResolver(VersionedComponentChooser componentChooser) {
        this.versionedComponentChooser = componentChooser;
    }

    public void add(ModuleComponentRepository<ModuleComponentGraphResolveState> repository) {
        repositories.add(repository);
        repositoryNames.add(repository.getName());
    }

    @Override
    public void resolve(ComponentIdentifier identifier, ComponentOverrideMetadata componentOverrideMetadata, BuildableComponentResolveResult result) {
        if (!(identifier instanceof ModuleComponentIdentifier)) {
            throw new UnsupportedOperationException("Can resolve meta-data for module components only.");
        }

        resolveModule((ModuleComponentIdentifier) identifier, componentOverrideMetadata, result);
    }

    @Override
    public boolean isFetchingMetadataCheap(ComponentIdentifier identifier) {
        if (identifier instanceof ModuleComponentIdentifier) {
            for (ModuleComponentRepository<ModuleComponentGraphResolveState> repository : repositories) {
                ModuleComponentRepositoryAccess<ModuleComponentGraphResolveState> localAccess = repository.getLocalAccess();
                MetadataFetchingCost fetchingCost = localAccess.estimateMetadataFetchingCost((ModuleComponentIdentifier) identifier);
                if (fetchingCost.isFast()) {
                    return true;
                } else if (fetchingCost.isExpensive()) {
                    return false;
                }
            }
        }
        return true;
    }

    private void resolveModule(ModuleComponentIdentifier identifier, ComponentOverrideMetadata componentOverrideMetadata, BuildableComponentResolveResult result) {
        LOGGER.debug("Attempting to resolve component for {} using repositories {}", identifier, repositoryNames);

        List<Throwable> errors = new ArrayList<>();

        List<ComponentMetaDataResolveState> resolveStates = new ArrayList<>();
        for (ModuleComponentRepository<ModuleComponentGraphResolveState> repository : repositories) {
            resolveStates.add(new ComponentMetaDataResolveState(identifier, componentOverrideMetadata, repository, versionedComponentChooser));
        }

        final RepositoryChainModuleResolution latestResolved = findBestMatch(resolveStates, errors);
        if (latestResolved != null) {
            LOGGER.debug("Using {} from {}", latestResolved.component.getId(), latestResolved.repository);
            for (Throwable error : errors) {
                LOGGER.debug("Discarding resolve failure.", error);
            }

            String repositoryName = latestResolved.repository.getName();
            result.resolved(latestResolved.component, new ModuleComponentGraphSpecificResolveState(repositoryName));
            return;
        }
        if (!errors.isEmpty()) {
            result.failed(new ModuleVersionResolveException(identifier, errors));
        } else {
            for (ComponentMetaDataResolveState resolveState : resolveStates) {
                resolveState.applyTo(result);
            }
            result.notFound(identifier);
        }
    }

    @Nullable
    private RepositoryChainModuleResolution findBestMatch(List<ComponentMetaDataResolveState> resolveStates, Collection<Throwable> failures) {
        LinkedList<ComponentMetaDataResolveState> queue = new LinkedList<>(resolveStates);

        LinkedList<ComponentMetaDataResolveState> missing = new LinkedList<>();

        // A first pass to do local resolves only
        RepositoryChainModuleResolution best = findBestMatch(queue, failures, missing);
        if (hasCriticalFailure(failures)) {
            return null;
        }
        if (best != null) {
            return best;
        }

        // Nothing found locally - try a remote search for all resolve states that were not yet searched remotely
        queue.addAll(missing);
        missing.clear();
        return findBestMatch(queue, failures, missing);
    }

    @Nullable
    private RepositoryChainModuleResolution findBestMatch(LinkedList<ComponentMetaDataResolveState> queue, Collection<Throwable> failures, Collection<ComponentMetaDataResolveState> missing) {
        RepositoryChainModuleResolution best = null;
        while (!queue.isEmpty()) {
            ComponentMetaDataResolveState request = queue.removeFirst();
            BuildableModuleComponentMetaDataResolveResult<ModuleComponentGraphResolveState> metaDataResolveResult;
            metaDataResolveResult = request.resolve();
            switch (metaDataResolveResult.getState()) {
                case Failed:
                    failures.add(metaDataResolveResult.getFailure());
                    if (isCriticalFailure(metaDataResolveResult.getFailure())) {
                        queue.clear();
                    }
                    break;
                case Missing:
                    // Queue this up for checking again later
                    if (request.canMakeFurtherAttempts()) {
                        missing.add(request);
                    }
                    break;
                case Resolved:
                    RepositoryChainModuleResolution moduleResolution = new RepositoryChainModuleResolution(request.repository, metaDataResolveResult.getMetaData());
                    if (!metaDataResolveResult.getMetaData().getMetadata().isMissing()) {
                        return moduleResolution;
                    }
                    best = best != null ? best : moduleResolution;
                    break;
                default:
                    throw new IllegalStateException("Unexpected state for resolution: " + metaDataResolveResult.getState());
            }
        }

        return best;
    }
}
