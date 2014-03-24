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
import org.gradle.api.internal.artifacts.ivyservice.BuildableComponentResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleVersionResolver;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher;
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

// TODO:DAZ This needs to be broken up
public class RepositoryChainDependencyResolver implements DependencyToModuleVersionResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryChainDependencyResolver.class);

    private final List<LocalAwareModuleVersionRepository> moduleVersionRepositories = new ArrayList<LocalAwareModuleVersionRepository>();
    private final List<String> moduleVersionRepositoryNames = new ArrayList<String>();
    private final VersionMatcher versionMatcher;
    private final LatestStrategy latestStrategy;
    private final Transformer<ModuleVersionMetaData, RepositoryChainModuleResolution> metaDataFactory;

    public RepositoryChainDependencyResolver(VersionMatcher versionMatcher, LatestStrategy latestStrategy, Transformer<ModuleVersionMetaData, RepositoryChainModuleResolution> metaDataFactory) {
        this.versionMatcher = versionMatcher;
        this.latestStrategy = latestStrategy;
        this.metaDataFactory = metaDataFactory;
    }

    public void add(LocalAwareModuleVersionRepository repository) {
        moduleVersionRepositories.add(repository);
        moduleVersionRepositoryNames.add(repository.getName());
    }

    public void resolve(DependencyMetaData dependency, BuildableComponentResolveResult result) {
        ModuleVersionSelector requested = dependency.getRequested();
        LOGGER.debug("Attempting to resolve module '{}' using repositories {}", requested, moduleVersionRepositoryNames);
        List<Throwable> errors = new ArrayList<Throwable>();
        final RepositoryChainModuleResolution latestResolved = findLatestModule(dependency, errors);
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
            result.notFound(requested);
        }
    }

    private RepositoryChainModuleResolution findLatestModule(DependencyMetaData dependency, Collection<Throwable> failures) {
        LinkedList<RepositoryResolveState> queue = new LinkedList<RepositoryResolveState>();
        for (LocalAwareModuleVersionRepository repository : moduleVersionRepositories) {
            queue.add(createRepositoryResolveState(repository, dependency));
        }
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

    private RepositoryResolveState createRepositoryResolveState(LocalAwareModuleVersionRepository repository, DependencyMetaData dependency) {
        if (versionMatcher.isDynamic(dependency.getRequested().getVersion())) {
            return new DynamicVersionRepositoryResolveState(repository);
        }
        return new StaticVersionRepositoryResolveState(repository);
    }

    private RepositoryChainModuleResolution findLatestModule(DependencyMetaData dependency, LinkedList<RepositoryResolveState> queue, Collection<Throwable> failures, Collection<RepositoryResolveState> missing) {
        boolean isStaticVersion = !versionMatcher.isDynamic(dependency.getRequested().getVersion());
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
        if (one.module == null || two.module == null) {
            return two.module == null ? one : two;
        }

        int comparison = latestStrategy.compare(one, two);

        if (comparison == 0) {
            if (one.isGeneratedModuleDescriptor() && !two.isGeneratedModuleDescriptor()) {
                return two;
            }
            return one;
        }

        return comparison < 0 ? two : one;
    }

    public static abstract class RepositoryResolveState {
        final LocalAwareModuleVersionRepository repository;
        final DefaultBuildableModuleVersionSelectionResolveResult selectionResult = new DefaultBuildableModuleVersionSelectionResolveResult();
        final DefaultBuildableModuleVersionMetaDataResolveResult resolveResult = new DefaultBuildableModuleVersionMetaDataResolveResult();
        boolean searchedLocally;
        boolean searchedRemotely;

        public RepositoryResolveState(LocalAwareModuleVersionRepository repository) {
            this.repository = repository;
        }

        void resolve(DependencyMetaData dependency) {
            if (!searchedLocally) {
                searchedLocally = true;
                process(dependency, new LocalModuleAccess());
            } else {
                searchedRemotely = true;
                process(dependency, new RemoteModuleAccess());
            }
            if (resolveResult.getState() == BuildableModuleVersionMetaDataResolveResult.State.Failed) {
                throw resolveResult.getFailure();
            }
        }

        protected abstract void process(DependencyMetaData dependency, ModuleAccess localModuleAccess);

        public boolean canMakeFurtherAttempts() {
            return !searchedRemotely;
        }

        protected interface ModuleAccess {
            void listModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result);
            void getDependency(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result);
        }

        protected class LocalModuleAccess implements ModuleAccess {
            public void listModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
                repository.localListModuleVersions(dependency, result);
            }

            public void getDependency(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result) {
                repository.getLocalDependency(dependency, result);
            }
        }

        protected class RemoteModuleAccess implements ModuleAccess {
            public void listModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
                repository.listModuleVersions(dependency, result);
            }

            public void getDependency(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result) {
                repository.getDependency(dependency, result);
            }
        }
    }

    private class StaticVersionRepositoryResolveState extends RepositoryResolveState {

        public StaticVersionRepositoryResolveState(LocalAwareModuleVersionRepository repository) {
            super(repository);
        }

        protected void process(DependencyMetaData dependency, ModuleAccess moduleAccess) {
            moduleAccess.getDependency(dependency, resolveResult);
        }
    }

    private class DynamicVersionRepositoryResolveState extends RepositoryResolveState {

        public DynamicVersionRepositoryResolveState(LocalAwareModuleVersionRepository repository) {
            super(repository);
        }

        protected void process(DependencyMetaData dependency, ModuleAccess moduleAccess) {
            moduleAccess.listModuleVersions(dependency, selectionResult);
            switch (selectionResult.getState()) {
                case Failed:
                    resolveResult.failed(selectionResult.getFailure());
                    break;
                case ProbablyListed:
                    if (!resolveDependency(dependency, moduleAccess)) {
                        resolveResult.probablyMissing();
                    }
                    break;
                case Listed:
                    if (!resolveDependency(dependency, moduleAccess)) {
                        resolveResult.missing();
                    }
            }
        }

        private boolean resolveDependency(DependencyMetaData dependency, ModuleAccess moduleAccess) {
            if (versionMatcher.needModuleMetadata(dependency.getRequested().getVersion())) {
                return getBestMatchingDependencyWithMetaData(dependency, moduleAccess);
            } else {
                return getBestMatchingDependency(dependency, moduleAccess);
            }
        }

        private boolean getBestMatchingDependency(DependencyMetaData dependency, ModuleAccess moduleAccess) {
            ModuleVersionSelector selector = dependency.getRequested();
            for (Versioned candidate : selectionResult.getVersions().sortLatestFirst(latestStrategy)) {
                if (versionMatcher.accept(selector.getVersion(), candidate.getVersion())) {
                    moduleAccess.getDependency(dependency.withRequestedVersion(candidate.getVersion()), resolveResult);
                    return true;
                }
            }
            return false;
        }

        private boolean getBestMatchingDependencyWithMetaData(DependencyMetaData dependency, ModuleAccess moduleAccess) {
            ModuleVersionSelector selector = dependency.getRequested();
            for (Versioned candidate : selectionResult.getVersions().sortLatestFirst(latestStrategy)) {
                // Resolve the metadata
                DependencyMetaData moduleVersionDependency = dependency.withRequestedVersion(candidate.getVersion());
                moduleAccess.getDependency(moduleVersionDependency, resolveResult);
                if (resolveResult.getState() != BuildableModuleVersionMetaDataResolveResult.State.Resolved) {
                    // Couldn't load listed module
                    LOGGER.warn("Could not load metadata for '{}' of listed module '{}' - ignoring.", candidate, selector);
                    resolveResult.reset();
                    return true;
                }
                if (versionMatcher.accept(selector.getVersion(), resolveResult.getMetaData())) {
                    // We already resolved the correct module.
                    return true;
                }
                resolveResult.reset();
            }
            return false;
        }
    }

}
