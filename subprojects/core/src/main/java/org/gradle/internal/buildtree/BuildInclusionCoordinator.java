/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.CompositeBuildParticipantBuildState;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.composite.IncludedBuildInternal;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.work.Synchronizer;
import org.gradle.internal.work.WorkerLeaseService;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Coordinates inclusion of builds across the build tree.
 */
@ServiceScope(Scope.BuildTree.class)
public class BuildInclusionCoordinator {
    private final Set<IncludedBuildState> loadedBuilds = new CopyOnWriteArraySet<>();
    private final List<IncludedBuildState> libraryBuilds = new CopyOnWriteArrayList<>();
    private final GlobalDependencySubstitutionRegistry substitutionRegistry;
    private final WorkerLeaseService workerLeaseService;
    private boolean registerRootSubstitutions;
    //    private final Set<BuildState> registering = ConcurrentHashMap.newKeySet();
    private final Map<BuildState, Synchronizer> registering = new ConcurrentHashMap<>();

    public BuildInclusionCoordinator(GlobalDependencySubstitutionRegistry substitutionRegistry, WorkerLeaseService workerLeaseService) {
        this.substitutionRegistry = substitutionRegistry;
        this.workerLeaseService = workerLeaseService;
    }

    public void prepareForInclusion(IncludedBuildState build, boolean asPlugin) {
        if (loadedBuilds.add(build)) {
            // Load projects (e.g. by running the settings script, etc.) only the first time the build is included by another build.
            // This is to deal with cycles and the build being included multiple times in the tree
            build.ensureProjectsLoaded();
        }
        if (!asPlugin && !libraryBuilds.contains(build)) {
            libraryBuilds.add(build);
        }
    }

    public void prepareRootBuildForInclusion() {
        registerRootSubstitutions = true;
    }

    private void registerGlobalLibrarySubstitutions() {
        for (IncludedBuildState includedBuild : libraryBuilds) {
            doRegisterSubstitutions(includedBuild);
        }
    }

    public void registerSubstitutionsAvailableFor(BuildState build) {
        if (build instanceof RootBuildState) {
            registerGlobalLibrarySubstitutions();
        } else {
            registering.computeIfAbsent(build, b -> workerLeaseService.newResource()).withLock(() -> {
                makeSubstitutionsAvailableFor(build);
            });
        }
    }

    public void registerSubstitutionsProvidedBy(BuildState build) {
        if (build instanceof RootBuildState && registerRootSubstitutions) {
            // Make root build substitutions available
            doRegisterSubstitutions((RootBuildState) build);
        }
    }

    public void prepareForPluginResolution(IncludedBuildState build) {
//        synchronized (registering) {
//            if (!registering.add(build)) {
//                return;
//            }
//            try {
//                build.ensureProjectsConfigured();
//                makeSubstitutionsAvailableFor(build, new HashSet<>());
//            } finally {
//                registering.remove(build);
//            }
//        }
        registering.computeIfAbsent(build, b -> workerLeaseService.newResource()).withLock(() -> {
            build.ensureProjectsConfigured();
            makeSubstitutionsAvailableFor(build);
        });
    }


    private void makeSubstitutionsAvailableFor(BuildState build) {
        Deque<BuildState> stack = new ArrayDeque<>();
        Set<BuildState> seen = new HashSet<>();

        stack.push(build);
        seen.add(build);

        while (!stack.isEmpty()) {
            BuildState current = stack.pop();

            for (IncludedBuildInternal reference : current.getMutableModel().includedBuilds()) {
                BuildState child = reference.getTarget();

                if (seen.add(child) && child instanceof IncludedBuildState) {
                    registering.computeIfAbsent(child, c -> workerLeaseService.newResource()).withLock(() -> {
                            substitutionRegistry.registerSubstitutionsFor((IncludedBuildState) child);
                        }
                    );
                    stack.push(child);
                }
            }
        }
    }


    private void makeSubstitutionsAvailableFor(BuildState build, Set<BuildState> seen) {
        // A build can see all the builds that it includes
        if (!seen.add(build)) {
            return;
        }
//        synchronized (registering) {
//        boolean added = registering.add(build);
//        try {
//            for (IncludedBuildInternal reference : build.getMutableModel().includedBuilds()) {
//                BuildState target = reference.getTarget();
//                if (!registering.contains(target) && target instanceof IncludedBuildState) {
//                    doRegisterSubstitutions((IncludedBuildState) target);
//                    makeSubstitutionsAvailableFor(target, seen);
//                }
//            }
//        } finally {
//            if (added) {
//                registering.remove(build);
//            }
//            }
//        }
    }

    private void doRegisterSubstitutions(CompositeBuildParticipantBuildState build) {
//        synchronized (registering) {
//        registering.add(build);
        try {
            substitutionRegistry.registerSubstitutionsFor(build);
        } finally {
//            registering.remove(build);
        }
//        }
    }
}
