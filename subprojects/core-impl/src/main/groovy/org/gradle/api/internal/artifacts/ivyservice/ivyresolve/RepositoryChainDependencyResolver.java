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
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.BuildableComponentResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleVersionResolver;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException;
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class RepositoryChainDependencyResolver implements DependencyToModuleVersionResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryChainDependencyResolver.class);

    private final List<ModuleComponentRepository> repositories = new ArrayList<ModuleComponentRepository>();
    private final List<String> repositoryNames = new ArrayList<String>();
    private final ComponentChooser componentChooser;
    private final Transformer<ModuleVersionMetaData, RepositoryChainModuleResolution> metaDataFactory;

    public RepositoryChainDependencyResolver(ComponentChooser componentChooser, Transformer<ModuleVersionMetaData, RepositoryChainModuleResolution> metaDataFactory) {
        this.componentChooser = componentChooser;
        this.metaDataFactory = metaDataFactory;
    }

    public void add(ModuleComponentRepository repository) {
        repositories.add(repository);
        repositoryNames.add(repository.getName());
    }

    public void resolve(DependencyMetaData dependency, BuildableComponentResolveResult result) {
        ModuleVersionSelector requested = dependency.getRequested();
        LOGGER.debug("Attempting to resolve module '{}' using repositories {}", requested, repositoryNames);
        List<Throwable> errors = new ArrayList<Throwable>();

        boolean dynamicSelector = componentChooser.canSelectMultipleComponents(dependency.getRequested());
        List<RepositoryResolveState> resolveStates = new ArrayList<RepositoryResolveState>();
        for (ModuleComponentRepository repository : repositories) {
            resolveStates.add(createRepositoryResolveState(repository, dynamicSelector));
        }

        final RepositoryChainModuleResolution latestResolved = findLatestModule(dependency, resolveStates, dynamicSelector, errors);
        if (latestResolved != null) {
            LOGGER.debug("Using module '{}' from repository '{}'", latestResolved.module.getId(), latestResolved.repository.getName());
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
            if (dynamicSelector) {
                result.notFound(requested);
            } else {
                result.notFound(DefaultModuleVersionIdentifier.newId(requested.getGroup(), requested.getName(), requested.getVersion()));
            }
        }
    }

    private RepositoryChainModuleResolution findLatestModule(DependencyMetaData dependency, List<RepositoryResolveState> resolveStates, boolean dynamicSelector, Collection<Throwable> failures) {
        LinkedList<RepositoryResolveState> queue = new LinkedList<RepositoryResolveState>();
        queue.addAll(resolveStates);

        LinkedList<RepositoryResolveState> missing = new LinkedList<RepositoryResolveState>();

        // A first pass to do local resolves only
        RepositoryChainModuleResolution best = findLatestModule(dependency, dynamicSelector, queue, failures, missing);
        if (best != null) {
            return best;
        }

        // Nothing found - do a second pass
        queue.addAll(missing);
        missing.clear();
        return findLatestModule(dependency, dynamicSelector, queue, failures, missing);
    }

    private RepositoryResolveState createRepositoryResolveState(ModuleComponentRepository repository, boolean isDynamicSelector) {
        if (isDynamicSelector) {
            return new DynamicVersionRepositoryResolveState(repository, componentChooser);
        }
        return new StaticVersionRepositoryResolveState(repository);
    }

    private RepositoryChainModuleResolution findLatestModule(DependencyMetaData dependency, boolean dynamicSelector, LinkedList<RepositoryResolveState> queue, Collection<Throwable> failures, Collection<RepositoryResolveState> missing) {
        boolean isStaticVersion = !dynamicSelector;
        RepositoryChainModuleResolution best = null;
        while (!queue.isEmpty()) {
            RepositoryResolveState request = queue.removeFirst();
            try {
                request.resolve(dependency);
            } catch (Throwable t) {
                failures.add(t);
                continue;
            }
            switch (request.resolveResult.getState()) {
                case Missing:
                    break;
                case ProbablyMissing:
                    // Queue this up for checking again later
                    if (request.canMakeFurtherAttempts()) {
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
                    RepositoryChainModuleResolution moduleResolution = new RepositoryChainModuleResolution(request.repository, request.resolveResult.getMetaData(), request.resolveResult.getModuleSource());
                    if (isStaticVersion && !moduleResolution.isGeneratedModuleDescriptor()) {
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

    public static abstract class RepositoryResolveState {
        private final DefaultBuildableModuleVersionMetaDataResolveResult resolveResult = new DefaultBuildableModuleVersionMetaDataResolveResult();
        final ModuleComponentRepository repository;

        private boolean searchedLocally;
        boolean searchedRemotely;

        public RepositoryResolveState(ModuleComponentRepository repository) {
            this.repository = repository;
        }

        void resolve(DependencyMetaData dependency) {
            if (!searchedLocally) {
                searchedLocally = true;
                process(dependency, repository.getLocalAccess(), resolveResult);
            } else {
                searchedRemotely = true;
                process(dependency, repository.getRemoteAccess(), resolveResult);
            }
            if (resolveResult.getState() == BuildableModuleVersionMetaDataResolveResult.State.Failed) {
                throw resolveResult.getFailure();
            }
        }

        protected abstract void process(DependencyMetaData dependency, ModuleComponentRepositoryAccess localModuleAccess, BuildableModuleVersionMetaDataResolveResult resolveResult);

        protected void applyTo(ResourceAwareResolveResult result) {
            resolveResult.applyTo(result);
        }

        public boolean canMakeFurtherAttempts() {
            return !searchedRemotely;
        }
    }

    private class StaticVersionRepositoryResolveState extends RepositoryResolveState {

        public StaticVersionRepositoryResolveState(ModuleComponentRepository repository) {
            super(repository);
        }

        protected void process(DependencyMetaData dependency, ModuleComponentRepositoryAccess moduleAccess, BuildableModuleVersionMetaDataResolveResult resolveResult) {
            moduleAccess.resolveComponentMetaData(dependency, DefaultModuleComponentIdentifier.newId(dependency.getRequested().getGroup(), dependency.getRequested().getName(), dependency.getRequested().getVersion()), resolveResult);
        }
    }

    private static class DynamicVersionRepositoryResolveState extends RepositoryResolveState {
        final DefaultBuildableModuleVersionSelectionResolveResult selectionResult = new DefaultBuildableModuleVersionSelectionResolveResult();
        private final ComponentChooser versionSelector;

        public DynamicVersionRepositoryResolveState(ModuleComponentRepository repository, ComponentChooser versionSelector) {
            super(repository);
            this.versionSelector = versionSelector;
        }

        protected void process(DependencyMetaData dependency, ModuleComponentRepositoryAccess moduleAccess, BuildableModuleVersionMetaDataResolveResult resolveResult) {
            moduleAccess.listModuleVersions(dependency, selectionResult);
            switch (selectionResult.getState()) {
                case Failed:
                    resolveResult.failed(selectionResult.getFailure());
                    break;
                case ProbablyListed:
                    if (!resolveDependency(dependency, moduleAccess, resolveResult)) {
                        resolveResult.probablyMissing();
                    }
                    break;
                case Listed:
                    if (!resolveDependency(dependency, moduleAccess, resolveResult)) {
                        resolveResult.missing();
                    }
            }
        }

        @Override
        protected void applyTo(ResourceAwareResolveResult result) {
            selectionResult.applyTo(result);
            super.applyTo(result);
        }

        private boolean resolveDependency(DependencyMetaData dependency, ModuleComponentRepositoryAccess moduleAccess, BuildableModuleVersionMetaDataResolveResult resolveResult) {
            ModuleComponentIdentifier componentIdentifier = versionSelector.choose(selectionResult.getVersions(), dependency, moduleAccess);
            if (componentIdentifier == null) {
                return false;
            }
            dependency = dependency.withRequestedVersion(componentIdentifier.getVersion());
            moduleAccess.resolveComponentMetaData(dependency, componentIdentifier, resolveResult);
            return true;
        }
    }

}
