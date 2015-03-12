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
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class DynamicVersionResolver implements DependencyToComponentIdResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicVersionResolver.class);

    private final List<ModuleComponentRepository> repositories = new ArrayList<ModuleComponentRepository>();
    private final List<String> repositoryNames = new ArrayList<String>();
    private final VersionedComponentChooser versionedComponentChooser;
    private final Transformer<ModuleComponentResolveMetaData, RepositoryChainModuleResolution> metaDataFactory;

    public DynamicVersionResolver(VersionedComponentChooser versionedComponentChooser, Transformer<ModuleComponentResolveMetaData, RepositoryChainModuleResolution> metaDataFactory) {
        this.versionedComponentChooser = versionedComponentChooser;
        this.metaDataFactory = metaDataFactory;
    }

    public void add(ModuleComponentRepository repository) {
        repositories.add(repository);
        repositoryNames.add(repository.getName());
    }

    public void resolve(DependencyMetaData dependency, BuildableComponentIdResolveResult result) {
        ModuleVersionSelector requested = dependency.getRequested();
        LOGGER.debug("Attempting to resolve {} using repositories {}", requested, repositoryNames);
        List<Throwable> errors = new ArrayList<Throwable>();

        List<RepositoryResolveState> resolveStates = new ArrayList<RepositoryResolveState>();
        for (ModuleComponentRepository repository : repositories) {
            resolveStates.add(new RepositoryResolveState(repository));
        }

        final RepositoryChainModuleResolution latestResolved = findLatestModule(dependency, resolveStates, errors);
        if (latestResolved != null) {
            LOGGER.debug("Using {} from {}", latestResolved.module.getId(), latestResolved.repository);
            for (Throwable error : errors) {
                LOGGER.debug("Discarding resolve failure.", error);
            }

            result.resolved(metaDataFactory.transform(latestResolved));
            return;
        }
        if (!errors.isEmpty()) {
            result.failed(new ModuleVersionResolveException(requested, errors));
        } else {
            for (RepositoryResolveState resolveState : resolveStates) {
                resolveState.applyTo(result);
            }
            result.notFound(requested);
        }
    }

    private RepositoryChainModuleResolution findLatestModule(DependencyMetaData dependency, List<RepositoryResolveState> resolveStates, Collection<Throwable> failures) {
        LinkedList<RepositoryResolveState> queue = new LinkedList<RepositoryResolveState>();
        queue.addAll(resolveStates);

        LinkedList<RepositoryResolveState> missing = new LinkedList<RepositoryResolveState>();

        // A first pass to do local resolves only
        RepositoryChainModuleResolution best = findLatestModule(dependency, queue, failures, missing);
        if (best != null) {
            return best;
        }

        // Nothing found - do a second pass
        queue.addAll(missing);
        missing.clear();
        return findLatestModule(dependency, queue, failures, missing);
    }

    private RepositoryChainModuleResolution findLatestModule(DependencyMetaData dependency, LinkedList<RepositoryResolveState> queue, Collection<Throwable> failures, Collection<RepositoryResolveState> missing) {
        RepositoryChainModuleResolution best = null;
        while (!queue.isEmpty()) {
            RepositoryResolveState request = queue.removeFirst();
            try {
                request.resolve(dependency);
            } catch (Throwable t) {
                failures.add(t);
                continue;
            }
            switch (request.metaDataResolveResult.getState()) {
                case Missing:
                    // Queue this up for checking again later
                    if (!request.metaDataResolveResult.isAuthoritative() && request.canMakeFurtherAttempts()) {
                        missing.add(request);
                    }
                    break;
                case Unknown:
                    // Resolve again now
                    if (request.canMakeFurtherAttempts()) {
                        queue.addFirst(request);
                    }
                    break;
                case Resolved:
                    RepositoryChainModuleResolution moduleResolution = new RepositoryChainModuleResolution(request.repository, request.metaDataResolveResult.getMetaData());
                    best = chooseBest(best, moduleResolution);
                    break;
                default:
                    throw new IllegalStateException("Unexpected state for resolution: " + request.metaDataResolveResult.getState());
            }
        }

        return best;
    }

    private RepositoryChainModuleResolution chooseBest(RepositoryChainModuleResolution one, RepositoryChainModuleResolution two) {
        if (one == null || two == null) {
            return two == null ? one : two;
        }
        return versionedComponentChooser.selectNewestComponent(one.module, two.module) == one.module ? one : two;
    }

    public class RepositoryResolveState {
        private final DefaultBuildableModuleComponentMetaDataResolveResult metaDataResolveResult = new DefaultBuildableModuleComponentMetaDataResolveResult();
        final DefaultBuildableModuleVersionListingResolveResult versionListingResult = new DefaultBuildableModuleVersionListingResolveResult();
        final ModuleComponentRepository repository;

        private boolean searchedLocally;
        boolean searchedRemotely;

        public RepositoryResolveState(ModuleComponentRepository repository) {
            this.repository = repository;
        }

        void resolve(DependencyMetaData dependency) {
            if (!searchedLocally) {
                searchedLocally = true;
                process(dependency, repository.getLocalAccess(), metaDataResolveResult);
            } else {
                searchedRemotely = true;
                process(dependency, repository.getRemoteAccess(), metaDataResolveResult);
            }
            if (metaDataResolveResult.getState() == BuildableModuleComponentMetaDataResolveResult.State.Failed) {
                throw metaDataResolveResult.getFailure();
            }
        }

        protected void process(DependencyMetaData dynamicVersionDependency, ModuleComponentRepositoryAccess moduleAccess, BuildableModuleComponentMetaDataResolveResult resolveResult) {
            moduleAccess.listModuleVersions(dynamicVersionDependency, versionListingResult);
            switch (versionListingResult.getState()) {
                case Failed:
                    resolveResult.failed(versionListingResult.getFailure());
                    break;
                case Listed:
                    selectMatchingVersionAndResolve(dynamicVersionDependency, moduleAccess, resolveResult);
                    break;
                case Unknown:
                    break;
                default:
                    throw new IllegalStateException("Unexpected state for version list result.");
            }
        }

        private void selectMatchingVersionAndResolve(DependencyMetaData dynamicVersionDependency, ModuleComponentRepositoryAccess moduleAccess, BuildableModuleComponentMetaDataResolveResult resolveResult) {
            // TODO - reuse metaData if it was already fetched to select the component from the version list
            DefaultBuildableComponentSelectionResult componentSelectionResult = new DefaultBuildableComponentSelectionResult();
            versionedComponentChooser.selectNewestMatchingComponent(versionListingResult.getVersions(), dynamicVersionDependency, moduleAccess, componentSelectionResult);
            switch (componentSelectionResult.getState()) {
                // No version matching list: component is missing
                case NoMatch:
                    componentSelectionResult.applyTo(resolveResult);
                    resolveResult.missing();
                    resolveResult.setAuthoritative(versionListingResult.isAuthoritative());
                    break;
                // Found version matching in list: resolve component
                case Match:
                    ModuleComponentIdentifier selectedComponentId = componentSelectionResult.getMatch();
                    DependencyMetaData selectedVersionDependency = dynamicVersionDependency.withRequestedVersion(selectedComponentId.getVersion());
                    moduleAccess.resolveComponentMetaData(selectedVersionDependency, selectedComponentId, resolveResult);
                    break;
                default:
                    throw new IllegalStateException("Unexpected state for component selection result.");
            }
        }

        protected void applyTo(ResourceAwareResolveResult result) {
            metaDataResolveResult.applyTo(result);
            versionListingResult.applyTo(result);
        }

        public boolean canMakeFurtherAttempts() {
            return !searchedRemotely;
        }
    }
}
