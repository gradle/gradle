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

import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.DependencyToComponentResolver;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.ResourceAwareResolveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class RepositoryChainDependencyResolver implements DependencyToComponentResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryChainDependencyResolver.class);

    private final List<ModuleComponentRepository> repositories = new ArrayList<ModuleComponentRepository>();
    private final List<String> repositoryNames = new ArrayList<String>();
    private final ComponentChooser componentChooser;
    private final Transformer<ModuleComponentResolveMetaData, RepositoryChainModuleResolution> metaDataFactory;

    public RepositoryChainDependencyResolver(ComponentChooser componentChooser, Transformer<ModuleComponentResolveMetaData, RepositoryChainModuleResolution> metaDataFactory) {
        this.componentChooser = componentChooser;
        this.metaDataFactory = metaDataFactory;
    }

    public void add(ModuleComponentRepository repository) {
        repositories.add(repository);
        repositoryNames.add(repository.getName());
    }

    public void resolve(DependencyMetaData dependency, BuildableComponentResolveResult result) {
        ModuleVersionSelector requested = dependency.getRequested();
        LOGGER.debug("Attempting to resolve {} using repositories {}", requested, repositoryNames);
        ModuleComponentIdentifier moduleComponentIdentifier = new DefaultModuleComponentIdentifier(requested.getGroup(), requested.getName(), requested.getVersion());
        ModuleVersionIdentifier moduleVersionIdentifier = new DefaultModuleVersionIdentifier(requested.getGroup(), requested.getName(), requested.getVersion());

        List<Throwable> errors = new ArrayList<Throwable>();

        List<RepositoryResolveState> resolveStates = new ArrayList<RepositoryResolveState>();
        for (ModuleComponentRepository repository : repositories) {
            resolveStates.add(new RepositoryResolveState(dependency, moduleComponentIdentifier, repository, componentChooser));
        }

        final RepositoryChainModuleResolution latestResolved = findBestMatch(resolveStates, errors);
        if (latestResolved != null) {
            LOGGER.debug("Using {} from {}", latestResolved.module.getId(), latestResolved.repository);
            for (Throwable error : errors) {
                LOGGER.debug("Discarding resolve failure.", error);
            }

            result.resolved(metaDataFactory.transform(latestResolved));
            return;
        }
        if (!errors.isEmpty()) {
            result.failed(new ModuleVersionResolveException(moduleComponentIdentifier, errors));
        } else {
            for (RepositoryResolveState resolveState : resolveStates) {
                resolveState.applyTo(result);
            }
            result.notFound(moduleVersionIdentifier);
        }
    }

    private RepositoryChainModuleResolution findBestMatch(List<RepositoryResolveState> resolveStates, Collection<Throwable> failures) {
        LinkedList<RepositoryResolveState> queue = new LinkedList<RepositoryResolveState>();
        queue.addAll(resolveStates);

        LinkedList<RepositoryResolveState> missing = new LinkedList<RepositoryResolveState>();

        // A first pass to do local resolves only
        RepositoryChainModuleResolution best = findBestMatch(queue, failures, missing);
        if (best != null) {
            return best;
        }

        // Nothing found - do a second pass
        queue.addAll(missing);
        missing.clear();
        return findBestMatch(queue, failures, missing);
    }

    private RepositoryChainModuleResolution findBestMatch(LinkedList<RepositoryResolveState> queue, Collection<Throwable> failures, Collection<RepositoryResolveState> missing) {
        RepositoryChainModuleResolution best = null;
        while (!queue.isEmpty()) {
            RepositoryResolveState request = queue.removeFirst();
            try {
                request.resolve();
            } catch (Throwable t) {
                failures.add(t);
                continue;
            }
            switch (request.resolveResult.getState()) {
                case Missing:
                    // Queue this up for checking again later
                    if (request.canMakeFurtherAttempts()) {
                        missing.add(request);
                    }
                    break;
                case Resolved:
                    RepositoryChainModuleResolution moduleResolution = new RepositoryChainModuleResolution(request.repository, request.resolveResult.getMetaData());
                    if (!moduleResolution.isGeneratedModuleDescriptor()) {
                        return moduleResolution;
                    }
                    best = chooseBest(best, moduleResolution);
                    break;
                default:
                    throw new IllegalStateException("Unexpected state for resolution: " + request.resolveResult.getState());
            }
        }

        return best;
    }

    private RepositoryChainModuleResolution chooseBest(RepositoryChainModuleResolution one, RepositoryChainModuleResolution two) {
        if (one == null || two == null) {
            return two == null ? one : two;
        }
        return componentChooser.choose(one.module, two.module) == one.module ? one : two;
    }

    private static class RepositoryResolveState {
        private final DefaultBuildableModuleComponentMetaDataResolveResult resolveResult = new DefaultBuildableModuleComponentMetaDataResolveResult();
        private final ComponentChooser componentChooser;
        private final DependencyMetaData dependency;
        private final ModuleComponentIdentifier componentIdentifier;
        final ModuleComponentRepository repository;

        private boolean searchedLocally;
        boolean searchedRemotely;

        public RepositoryResolveState(DependencyMetaData dependency, ModuleComponentIdentifier componentIdentifier, ModuleComponentRepository repository, ComponentChooser componentChooser) {
            this.dependency = dependency;
            this.componentIdentifier = componentIdentifier;
            this.repository = repository;
            this.componentChooser = componentChooser;
        }

        void resolve() {
            if (!searchedLocally) {
                searchedLocally = true;
                process(dependency, componentIdentifier, repository.getLocalAccess(), resolveResult);
                if (resolveResult.getState() != BuildableModuleComponentMetaDataResolveResult.State.Unknown) {
                    if (resolveResult.isAuthoritative()) {
                        // Don't bother searching remotely
                        searchedRemotely = true;
                    }
                    return;
                }
                // If unknown, try a remote search
            }
            if (!searchedRemotely) {
                searchedRemotely = true;
                process(dependency, componentIdentifier, repository.getRemoteAccess(), resolveResult);
            }
        }

        protected void process(DependencyMetaData dependency, ModuleComponentIdentifier componentIdentifier, ModuleComponentRepositoryAccess moduleAccess, BuildableModuleComponentMetaDataResolveResult resolveResult) {
            moduleAccess.resolveComponentMetaData(dependency, componentIdentifier, resolveResult);
            if (resolveResult.getState() == BuildableModuleComponentMetaDataResolveResult.State.Failed) {
                throw resolveResult.getFailure();
            }
            if (resolveResult.getState() == BuildableModuleComponentMetaDataResolveResult.State.Resolved) {
                if (componentChooser.isRejectedByRules(componentIdentifier, dependency, moduleAccess)) {
                    resolveResult.missing();
                }
            }
        }

        protected void applyTo(ResourceAwareResolveResult result) {
            resolveResult.applyTo(result);
        }

        public boolean canMakeFurtherAttempts() {
            return !searchedRemotely;
        }
    }
}
