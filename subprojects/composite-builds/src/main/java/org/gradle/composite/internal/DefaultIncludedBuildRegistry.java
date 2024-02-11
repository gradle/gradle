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
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier;
import org.gradle.initialization.buildsrc.BuildSrcDetector;
import org.gradle.internal.build.BuildAddedListener;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildFactory;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.build.NestedBuildState;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.build.StandAloneNestedBuild;
import org.gradle.internal.buildtree.NestedBuildTree;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class DefaultIncludedBuildRegistry implements BuildStateRegistry, Stoppable {
    private final IncludedBuildFactory includedBuildFactory;
    private final BuildAddedListener buildAddedBroadcaster;
    private final BuildStateFactory buildStateFactory;

    // TODO: Locking around the following state
    private RootBuildState rootBuild;
    private final Map<BuildIdentifier, BuildState> buildsByIdentifier = new HashMap<>();
    private final Map<BuildState, StandAloneNestedBuild> buildSrcBuildsByOwner = new HashMap<>();
    private final Map<File, IncludedBuildState> includedBuildsByRootDir = new LinkedHashMap<>();
    private final Map<File, NestedBuildState> nestedBuildsByRootDir = new LinkedHashMap<>();
    private final Map<Path, File> nestedBuildDirectoriesByPath = new LinkedHashMap<>();
    private final Deque<IncludedBuildState> pendingIncludedBuilds = new ArrayDeque<>();

    public DefaultIncludedBuildRegistry(IncludedBuildFactory includedBuildFactory, ListenerManager listenerManager, BuildStateFactory buildStateFactory) {
        this.includedBuildFactory = includedBuildFactory;
        this.buildAddedBroadcaster = listenerManager.getBroadcaster(BuildAddedListener.class);
        this.buildStateFactory = buildStateFactory;
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
        rootBuild = buildStateFactory.createRootBuild(buildDefinition);
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
        maybeAddBuildSrcBuild(build);
    }

    @Override
    public IncludedBuildState addIncludedBuild(BuildDefinition buildDefinition) {
        return registerBuild(buildDefinition, false, null);
    }

    @Override
    public IncludedBuildState addIncludedBuild(BuildDefinition buildDefinition, Path buildPath) {
        return registerBuild(buildDefinition, false, buildPath);
    }

    @Override
    public synchronized IncludedBuildState addImplicitIncludedBuild(BuildDefinition buildDefinition) {
        // TODO: synchronization with other methods
        IncludedBuildState includedBuild = includedBuildsByRootDir.get(buildDefinition.getBuildRootDir());
        if (includedBuild == null) {
            includedBuild = registerBuild(buildDefinition, true, null);
        }
        return includedBuild;
    }

    @Override
    public Collection<IncludedBuildState> getIncludedBuilds() {
        return includedBuildsByRootDir.values();
    }

    @Override
    public IncludedBuildState getIncludedBuild(BuildIdentifier buildIdentifier) {
        BuildState includedBuildState = findBuild(buildIdentifier);
        if (!(includedBuildState instanceof IncludedBuildState)) {
            throw new IllegalArgumentException("Could not find " + buildIdentifier);
        }
        return (IncludedBuildState) includedBuildState;
    }

    @Override
    public BuildState getBuild(BuildIdentifier buildIdentifier) {
        BuildState buildState = findBuild(buildIdentifier);
        if (buildState == null) {
            throw new IllegalArgumentException("Could not find " + buildIdentifier);
        }
        return buildState;
    }

    @Override
    public BuildState findBuild(BuildIdentifier buildIdentifier) {
        return buildsByIdentifier.get(buildIdentifier);
    }

    @Override
    public void finalizeIncludedBuilds() {
        while (!pendingIncludedBuilds.isEmpty()) {
            IncludedBuildState build = pendingIncludedBuilds.removeFirst();
            assertNameDoesNotClashWithRootSubproject(build);
        }
    }

    @Override
    public StandAloneNestedBuild getBuildSrcNestedBuild(BuildState owner) {
        return buildSrcBuildsByOwner.get(owner);
    }

    private void maybeAddBuildSrcBuild(BuildState owner) {
        File buildSrcDir = new File(owner.getBuildRootDir(), SettingsInternal.BUILD_SRC);
        if (!BuildSrcDetector.isValidBuildSrcBuild(buildSrcDir)) {
            return;
        }

        BuildDefinition buildDefinition = buildStateFactory.buildDefinitionFor(buildSrcDir, owner);
        Path identityPath = assignPath(owner, buildDefinition.getName(), buildDefinition.getBuildRootDir());
        BuildIdentifier buildIdentifier = idFor(identityPath);
        StandAloneNestedBuild build = buildStateFactory.createNestedBuild(buildIdentifier, identityPath, buildDefinition, owner);
        buildSrcBuildsByOwner.put(owner, build);
        nestedBuildsByRootDir.put(buildSrcDir, build);
        addBuild(build);
    }

    @Override
    public NestedBuildTree addNestedBuildTree(BuildInvocationScopeId buildInvocationScopeId, BuildDefinition buildDefinition, BuildState owner, @Nullable String buildName) {
        if (buildDefinition.getName() != null || buildDefinition.getBuildRootDir() != null) {
            throw new UnsupportedOperationException("Not yet implemented."); // but should be
        }
        File dir = buildDefinition.getStartParameter().getCurrentDir();
        String name = MoreObjects.firstNonNull(buildName, dir.getName());
        validateNameIsNotBuildSrc(name, dir);
        Path identityPath = assignPath(owner, name, dir);
        BuildIdentifier buildIdentifier = idFor(identityPath);
        return buildStateFactory.createNestedTree(buildInvocationScopeId, buildDefinition, buildIdentifier, identityPath, owner);
    }

    @Override
    public void visitBuilds(Consumer<? super BuildState> visitor) {
        List<BuildState> ordered = new ArrayList<>(buildsByIdentifier.values());
        ordered.sort(Comparator.comparing(BuildState::getIdentityPath));
        for (BuildState buildState : ordered) {
            visitor.accept(buildState);
        }
    }

    private static void validateNameIsNotBuildSrc(String name, File dir) {
        if (SettingsInternal.BUILD_SRC.equals(name)) {
            throw new GradleException("Included build " + dir + " has build name 'buildSrc' which cannot be used as it is a reserved name.");
        }
    }

    private IncludedBuildState registerBuild(BuildDefinition buildDefinition, boolean isImplicit, @Nullable Path buildPath) {
        // TODO: synchronization
        File buildDir = buildDefinition.getBuildRootDir();
        if (buildDir == null) {
            throw new IllegalArgumentException("Included build must have a root directory defined");
        }
        IncludedBuildState includedBuild = includedBuildsByRootDir.get(buildDir);
        if (includedBuild == null) {
            if (rootBuild == null) {
                throw new IllegalStateException("No root build attached yet.");
            }
            String buildName = buildDefinition.getName();
            if (buildName == null) {
                throw new IllegalStateException("build name is required");
            }
            validateNameIsNotBuildSrc(buildName, buildDir);
            Path idPath = buildPath != null ? buildPath : assignPath(rootBuild, buildName, buildDir);
            BuildIdentifier buildIdentifier = idFor(idPath);

            includedBuild = includedBuildFactory.createBuild(buildIdentifier, buildDefinition, isImplicit, rootBuild);
            includedBuildsByRootDir.put(buildDir, includedBuild);
            nestedBuildsByRootDir.put(buildDir, includedBuild);
            pendingIncludedBuilds.add(includedBuild);
            addBuild(includedBuild);
        } else {
            if (includedBuild.isImplicitBuild() != isImplicit) {
                throw new IllegalStateException("Unexpected state for build.");
            }
            // TODO: verify that the build definition is the same
        }
        return includedBuild;
    }

    private static BuildIdentifier idFor(Path absoluteBuildPath) {
        return new DefaultBuildIdentifier(absoluteBuildPath);
    }

    private Path assignPath(BuildState owner, String name, File dir) {
        // Get the closest ancestor build of the build directory which we are currently adding
        Optional<Map.Entry<File, NestedBuildState>> parentBuild = nestedBuildsByRootDir.entrySet().stream()
            .filter(entry -> isPrefix(entry.getKey(), dir))
            .reduce((a, b) -> isPrefix(a.getKey(), b.getKey())
                ? b
                : a
            );
        // If there is an ancestor, then we use it to qualify the path of the build we are adding
        Path requestedPath = parentBuild.map(
            entry -> entry.getValue().getIdentityPath().append(Path.path(name))
        ).orElseGet(() -> owner.getIdentityPath().append(Path.path(name)));
        File existingForPath = nestedBuildDirectoriesByPath.putIfAbsent(requestedPath, dir);
        if (existingForPath != null) {
            throw new GradleException("Included build " + dir + " has build path " + requestedPath + " which is the same as included build " + existingForPath);
        }

        return requestedPath;
    }

    private static boolean isPrefix(File prefix, File toCheck) {
        return toCheck.toPath().toAbsolutePath().startsWith(prefix.toPath().toAbsolutePath());
    }

    @Override
    public void resetStateForAllBuilds() {
        for (BuildState build : buildsByIdentifier.values()) {
            build.resetModel();
        }
    }

    @Override
    public void stop() {
        CompositeStoppable.stoppable(buildsByIdentifier.values()).stop();
    }

    private void assertNameDoesNotClashWithRootSubproject(IncludedBuildState includedBuild) {
        if (rootBuild.getProjects().findProject(includedBuild.getIdentityPath()) != null) {
            throw new GradleException("Included build in " + includedBuild.getBuildRootDir() + " has name '" + includedBuild.getName() + "' which is the same as a project of the main build.");
        }
    }
}
