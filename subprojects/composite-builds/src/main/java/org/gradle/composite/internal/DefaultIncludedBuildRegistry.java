/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.composite.internal;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Maps;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.build.BuildAddedListener;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.build.NestedRootBuild;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.build.StandAloneNestedBuild;
import org.gradle.internal.buildtree.BuildTreeState;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Map;

@ServiceScope(Scopes.BuildTree.class)
public class DefaultIncludedBuildRegistry implements BuildStateRegistry, Stoppable {
    private final BuildTreeState owner;
    private final IncludedBuildFactory includedBuildFactory;
    private final IncludedBuildDependencySubstitutionsBuilder dependencySubstitutionsBuilder;
    private final GradleLauncherFactory gradleLauncherFactory;
    private final ListenerManager listenerManager;
    private final BuildAddedListener buildAddedBroadcaster;

    // TODO: Locking around this state
    private RootBuildState rootBuild;
    private final Map<BuildIdentifier, BuildState> buildsByIdentifier = Maps.newHashMap();
    private final Map<File, IncludedBuildState> includedBuildsByRootDir = Maps.newLinkedHashMap();
    private final Map<Path, File> includedBuildDirectoriesByPath = Maps.newLinkedHashMap();
    private final Deque<IncludedBuildState> pendingIncludedBuilds = new ArrayDeque<>();

    public DefaultIncludedBuildRegistry(BuildTreeState owner, IncludedBuildFactory includedBuildFactory, IncludedBuildDependencySubstitutionsBuilder dependencySubstitutionsBuilder, GradleLauncherFactory gradleLauncherFactory, ListenerManager listenerManager) {
        this.owner = owner;
        this.includedBuildFactory = includedBuildFactory;
        this.dependencySubstitutionsBuilder = dependencySubstitutionsBuilder;
        this.gradleLauncherFactory = gradleLauncherFactory;
        this.listenerManager = listenerManager;
        this.buildAddedBroadcaster = listenerManager.getBroadcaster(BuildAddedListener.class);
    }

    @Override
    public RootBuildState getRootBuild() {
        if (rootBuild == null) {
            throw new IllegalStateException("Root build is not defined.");
        }
        return rootBuild;
    }

    @Override
    public RootBuildState createRootBuild(BuildDefinition buildDefinition) {
        if (rootBuild != null) {
            throw new IllegalStateException("Root build already defined.");
        }
        rootBuild = new DefaultRootBuildState(buildDefinition, gradleLauncherFactory, listenerManager, owner);
        addBuild(rootBuild);
        return rootBuild;
    }

    @Override
    public void attachRootBuild(RootBuildState rootBuild) {
        if (this.rootBuild != null) {
            throw new IllegalStateException("Root build already defined.");
        }
        this.rootBuild = rootBuild;
        addBuild(rootBuild);
    }

    private void addBuild(BuildState build) {
        BuildState before = buildsByIdentifier.put(build.getBuildIdentifier(), build);
        if (before != null) {
            throw new IllegalArgumentException("Build is already registered: " + build.getBuildIdentifier());
        }
        buildAddedBroadcaster.buildAdded(build);
    }

    public boolean hasIncludedBuilds() {
        return !includedBuildsByRootDir.isEmpty();
    }

    @Override
    public Collection<IncludedBuildState> getIncludedBuilds() {
        return includedBuildsByRootDir.values();
    }

    @Override
    public IncludedBuildState addIncludedBuild(BuildDefinition buildDefinition) {
        return registerBuild(buildDefinition, false);
    }

    @Override
    public IncludedBuildState getIncludedBuild(final BuildIdentifier buildIdentifier) {
        BuildState includedBuildState = buildsByIdentifier.get(buildIdentifier);
        if (!(includedBuildState instanceof IncludedBuildState)) {
            throw new IllegalArgumentException("Could not find " + buildIdentifier);
        }
        return (IncludedBuildState) includedBuildState;
    }

    @Override
    public BuildState getBuild(BuildIdentifier buildIdentifier) {
        BuildState buildState = buildsByIdentifier.get(buildIdentifier);
        if (buildState == null) {
            throw new IllegalArgumentException("Could not find " + buildIdentifier);
        }
        return buildState;
    }

    @Override
    public void beforeConfigureRootBuild() {
        registerSubstitutions(includedBuildsByRootDir.values());
    }

    @Override
    public void finalizeIncludedBuilds() {
        SettingsInternal rootSettings = getRootBuild().getLoadedSettings();
        while (!pendingIncludedBuilds.isEmpty()) {
            IncludedBuildState build = pendingIncludedBuilds.removeFirst();
            SettingsInternal includedBuildSettings = build.loadSettings();
            String includedBuildRootProjectName = includedBuildSettings.getRootProject().getName();
            if (rootSettings.getRootProject().getName().equals(includedBuildRootProjectName)) {
                throw new GradleException("Included build in " + build.getRootDirectory() + " has the same root project name '" + includedBuildRootProjectName + "' as the main build.");
            }
        }
    }

    private void registerSubstitutions(Iterable<IncludedBuildState> includedBuilds) {
        for (IncludedBuildState includedBuild : includedBuilds) {
            dependencySubstitutionsBuilder.build(includedBuild);
        }
    }

    @Override
    public synchronized IncludedBuildState addImplicitIncludedBuild(BuildDefinition buildDefinition) {
        // TODO: synchronization with other methods
        IncludedBuildState includedBuild = includedBuildsByRootDir.get(buildDefinition.getBuildRootDir());
        if (includedBuild == null) {
            includedBuild = registerBuild(buildDefinition, true);
        }
        return includedBuild;
    }

    @Override
    public StandAloneNestedBuild addBuildSrcNestedBuild(BuildDefinition buildDefinition, BuildState owner) {
        if (!SettingsInternal.BUILD_SRC.equals(buildDefinition.getName())) {
            throw new IllegalStateException("Expected buildSrc build, got: " + buildDefinition.getName());
        }
        Path identityPath = assignPath(owner, buildDefinition.getName(), buildDefinition.getBuildRootDir());
        BuildIdentifier buildIdentifier = idFor(buildDefinition.getName());
        DefaultNestedBuild build = new DefaultNestedBuild(buildIdentifier, identityPath, buildDefinition, owner);
        addBuild(build);
        return build;
    }

    @Override
    public NestedRootBuild addNestedBuildTree(BuildDefinition buildDefinition, BuildState owner, String buildName) {
        if (buildDefinition.getName() != null || buildDefinition.getBuildRootDir() != null) {
            throw new UnsupportedOperationException("Not yet implemented."); // but should be
        }
        File dir = buildDefinition.getStartParameter().getCurrentDir();
        String name = MoreObjects.firstNonNull(buildName, dir.getName());
        validateNameIsNotBuildSrc(name, dir);
        Path identityPath = assignPath(owner, name, dir);
        BuildIdentifier buildIdentifier = idFor(name);
        RootOfNestedBuildTree rootOfNestedBuildTree = new RootOfNestedBuildTree(buildDefinition, buildIdentifier, identityPath, owner);
        // Attach the build only after it has been fully constructed.
        rootOfNestedBuildTree.attach();
        return rootOfNestedBuildTree;
    }

    private void validateNameIsNotBuildSrc(String name, File dir) {
        if (SettingsInternal.BUILD_SRC.equals(name)) {
            throw new GradleException("Included build " + dir + " has build name 'buildSrc' which cannot be used as it is a reserved name.");
        }
    }

    private IncludedBuildState registerBuild(BuildDefinition buildDefinition, boolean isImplicit) {
        // TODO: synchronization
        final File buildDir = buildDefinition.getBuildRootDir();
        if (buildDir == null) {
            throw new IllegalArgumentException("Included build must have a root directory defined");
        }
        if (rootBuild == null) {
            throw new IllegalStateException("No root build attached yet.");
        }
        IncludedBuildState includedBuild = includedBuildsByRootDir.get(buildDir);
        if (includedBuild == null) {
            String buildName = buildDefinition.getName();
            if (buildName == null) {
                throw new IllegalStateException("build name is required");
            }
            validateNameIsNotBuildSrc(buildName, buildDir);
            Path idPath = assignPath(rootBuild, buildDefinition.getName(), buildDir);
            BuildIdentifier buildIdentifier = idFor(buildName);

            includedBuild = includedBuildFactory.createBuild(buildIdentifier, idPath, buildDefinition, isImplicit, rootBuild);
            includedBuildsByRootDir.put(buildDir, includedBuild);
            pendingIncludedBuilds.add(includedBuild);
            addBuild(includedBuild);
        } else {
            if (includedBuild.isImplicitBuild() != isImplicit) {
                throw new IllegalStateException("Unexpected state for build.");
            }
        }
        // TODO: else, verify that the build definition is the same
        return includedBuild;
    }

    private BuildIdentifier idFor(String buildName) {
        BuildIdentifier buildIdentifier = new DefaultBuildIdentifier(buildName);

        // Create a synthetic id for the build, if the id is already used
        // Should instead use a structured id implementation of some kind instead
        for (int count = 1; buildsByIdentifier.containsKey(buildIdentifier); count++) {
            buildIdentifier = new DefaultBuildIdentifier(buildName + ":" + count);
        }
        return buildIdentifier;
    }

    private Path assignPath(BuildState owner, String name, File dir) {
        Path requestedPath = owner.getIdentityPath().append(Path.path(name));
        File existingForPath = includedBuildDirectoriesByPath.putIfAbsent(requestedPath, dir);
        if (existingForPath != null) {
            throw new GradleException("Included build " + dir + " has build path " + requestedPath + " which is the same as included build " + existingForPath);
        }

        SettingsInternal rootSettings = getRootBuild().getLoadedSettings();
        if (rootSettings.findProject(":" + name) != null) {
            throw new GradleException("Included build in " + dir + " has name '" + name + "' which is the same as a project of the main build.");
        }

        return requestedPath;
    }

    @Override
    public void stop() {
        CompositeStoppable.stoppable(buildsByIdentifier.values()).stop();
    }
}
