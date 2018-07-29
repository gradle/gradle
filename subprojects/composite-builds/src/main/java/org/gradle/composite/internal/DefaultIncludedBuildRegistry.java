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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.build.StandAloneNestedBuild;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.text.TreeFormatter;
import org.gradle.util.Path;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultIncludedBuildRegistry implements BuildStateRegistry, Stoppable {
    private final IncludedBuildFactory includedBuildFactory;
    private final ProjectStateRegistry projectRegistry;
    private final IncludedBuildDependencySubstitutionsBuilder dependencySubstitutionsBuilder;
    private final GradleLauncherFactory gradleLauncherFactory;
    private final ListenerManager listenerManager;
    private final ServiceRegistry rootServices;

    // TODO: Locking around this state
    private RootBuildState rootBuild;
    private final Map<BuildIdentifier, BuildState> builds = Maps.newHashMap();
    private final Map<File, IncludedBuildState> includedBuilds = Maps.newLinkedHashMap();
    private final List<IncludedBuildState> pendingIncludedBuilds = Lists.newArrayList();

    public DefaultIncludedBuildRegistry(IncludedBuildFactory includedBuildFactory, ProjectStateRegistry projectRegistry, IncludedBuildDependencySubstitutionsBuilder dependencySubstitutionsBuilder, GradleLauncherFactory gradleLauncherFactory, ListenerManager listenerManager, ServiceRegistry rootServices) {
        this.includedBuildFactory = includedBuildFactory;
        this.projectRegistry = projectRegistry;
        this.dependencySubstitutionsBuilder = dependencySubstitutionsBuilder;
        this.gradleLauncherFactory = gradleLauncherFactory;
        this.listenerManager = listenerManager;
        this.rootServices = rootServices;
    }

    @Override
    public RootBuildState getRootBuild() {
        if (rootBuild == null) {
            throw new IllegalStateException("Root build is not defined.");
        }
        return rootBuild;
    }

    @Override
    public RootBuildState addRootBuild(BuildDefinition buildDefinition, BuildRequestContext requestContext) {
        if (rootBuild != null) {
            throw new IllegalStateException("Root build already defined.");
        }
        rootBuild = new DefaultRootBuildState(buildDefinition, requestContext, gradleLauncherFactory, listenerManager, rootServices);
        addBuild(rootBuild);
        return rootBuild;
    }

    private void addBuild(BuildState build) {
        BuildState before = builds.put(build.getBuildIdentifier(), build);
        if (before != null) {
            throw new IllegalArgumentException("Build is already registered: " + build.getBuildIdentifier());
        }
    }

    public boolean hasIncludedBuilds() {
        return !includedBuilds.isEmpty();
    }

    @Override
    public Collection<IncludedBuildState> getIncludedBuilds() {
        return includedBuilds.values();
    }

    @Override
    public IncludedBuildState addIncludedBuild(BuildDefinition buildDefinition) {
         return registerBuild(buildDefinition, false);
    }

    @Override
    public IncludedBuildState getIncludedBuild(final BuildIdentifier buildIdentifier) {
        BuildState includedBuildState = builds.get(buildIdentifier);
        if (!(includedBuildState instanceof IncludedBuildState)) {
            throw new IllegalArgumentException("Could not find " + buildIdentifier);
        }
        return (IncludedBuildState) includedBuildState;
    }

    @Override
    public BuildState getBuild(BuildIdentifier buildIdentifier) {
        BuildState buildState = builds.get(buildIdentifier);
        if (buildState == null) {
            throw new IllegalArgumentException("Could not find " + buildIdentifier);
        }
        return buildState;
    }

    @Override
    public void register(BuildState build) {
        addBuild(build);
        if (build instanceof RootBuildState) {
            if (rootBuild != null) {
                throw new IllegalStateException("Root build already defined.");
            }
            rootBuild = (RootBuildState) build;
        }
    }

    @Override
    public void registerRootBuild(SettingsInternal settings) {
        validateIncludedBuilds(settings);

        projectRegistry.registerProjects(rootBuild);

        Collection<IncludedBuildState> includedBuilds = getIncludedBuilds();
        registerProjects(includedBuilds);
    }

    @Override
    public void beforeConfigureRootBuild() {
        registerSubstitutions(includedBuilds.values());
    }

    private void registerProjects(Collection<IncludedBuildState> includedBuilds) {
        for (IncludedBuildState includedBuild : includedBuilds) {
            projectRegistry.registerProjects(includedBuild);
        }
    }

    private void validateIncludedBuilds(SettingsInternal settings) {
        SetMultimap<String, IncludedBuildState> names = LinkedHashMultimap.create();
        while (!pendingIncludedBuilds.isEmpty()) {
            IncludedBuildState build = pendingIncludedBuilds.remove(0);
            // This implicitly loads the settings, possibly discovering more included builds
            // Should make this an explicit step instead
            String buildName = build.getName();
            names.put(buildName, build);
            if (settings.getRootProject().getName().equals(buildName)) {
                throw new GradleException("Included build in " + build.getRootDirectory() + " has the same root project name '" + buildName + "' as the main build.");
            }
            if (settings.findProject(":" + buildName) != null) {
                throw new GradleException("Included build in " + build.getRootDirectory() + " has a root project whose name '" + buildName + "' is the same as a project of the main build.");
            }
        }
        for (String buildNAme : names.keySet()) {
            Set<IncludedBuildState> buildsWithName = names.get(buildNAme);
            if (buildsWithName.size() > 1) {
                TreeFormatter visitor = new TreeFormatter();
                visitor.node("Multiple included builds have the same root project name '" + buildNAme + "'");
                visitor.startChildren();
                for (IncludedBuildState build : buildsWithName) {
                    visitor.node("Included build in " + build.getRootDirectory());
                }
                visitor.endChildren();
                throw new GradleException(visitor.toString());
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
        IncludedBuildState includedBuild = includedBuilds.get(buildDefinition.getBuildRootDir());
        if (includedBuild == null) {
            includedBuild = registerBuild(buildDefinition, true);
            projectRegistry.registerProjects(includedBuild);
        }
        return includedBuild;
    }

    @Override
    public StandAloneNestedBuild addNestedBuild(BuildDefinition buildDefinition, BuildState owner) {
        if (buildDefinition.getName() == null) {
            throw new UnsupportedOperationException("Not yet implemented."); // but should be
        }
        BuildIdentifier buildIdentifier = idFor(buildDefinition.getName());
        Path identityPath = pathFor(owner, buildIdentifier.getName());
        DefaultNestedBuild build = new DefaultNestedBuild(buildIdentifier, identityPath, buildDefinition, owner, new BuildStateListener() {
            @Override
            public void projectsKnown(BuildState build1) {
                projectRegistry.registerProjects(build1);
            }
        });
        addBuild(build);
        return build;
    }

    @Override
    public StandAloneNestedBuild addNestedBuildTree(BuildDefinition buildDefinition, BuildState owner) {
        if (buildDefinition.getName() != null || buildDefinition.getBuildRootDir() != null) {
            throw new UnsupportedOperationException("Not yet implemented."); // but should be
        }
        BuildIdentifier buildIdentifier = idFor(buildDefinition.getStartParameter().getCurrentDir().getName());
        Path identityPath = pathFor(owner, buildIdentifier.getName());
        return new RootOfNestedBuildTree(buildDefinition, buildIdentifier, identityPath, owner);
    }

    private IncludedBuildState registerBuild(BuildDefinition buildDefinition, boolean isImplicit) {
        // TODO: synchronization
        if (buildDefinition.getBuildRootDir() == null) {
            throw new IllegalArgumentException("Included build must have a root directory defined");
        }
        if (rootBuild == null) {
            throw new IllegalStateException("No root build attached yet.");
        }
        IncludedBuildState includedBuild = includedBuilds.get(buildDefinition.getBuildRootDir());
        if (includedBuild == null) {
            String buildName = buildDefinition.getBuildRootDir().getName();
            BuildIdentifier buildIdentifier = idFor(buildName);
            Path idPath = pathFor(rootBuild, buildIdentifier.getName());

            includedBuild = includedBuildFactory.createBuild(buildIdentifier, idPath, buildDefinition, isImplicit, rootBuild);
            includedBuilds.put(buildDefinition.getBuildRootDir(), includedBuild);
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
        for (int count = 1; builds.containsKey(buildIdentifier); count++) {
            buildIdentifier = new DefaultBuildIdentifier(buildName + ":" + count);
        }
        return buildIdentifier;
    }

    private Path pathFor(BuildState owner, String name) {
        return owner.getIdentityPath().append(Path.path(name));
    }

    @Override
    public void stop() {
        CompositeStoppable.stoppable(builds.values()).stop();
    }
}
