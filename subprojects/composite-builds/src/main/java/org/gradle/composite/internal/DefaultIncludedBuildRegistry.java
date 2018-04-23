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

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.specs.Spec;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.initialization.NestedBuildFactory;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.build.NestedBuildState;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
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
    // TODO: use some kind of build identifier as the key
    private DefaultRootBuildState rootBuild;
    private final Map<File, IncludedBuildState> includedBuilds = Maps.newLinkedHashMap();

    public DefaultIncludedBuildRegistry(IncludedBuildFactory includedBuildFactory, ProjectStateRegistry projectRegistry, IncludedBuildDependencySubstitutionsBuilder dependencySubstitutionsBuilder, GradleLauncherFactory gradleLauncherFactory, ListenerManager listenerManager, ServiceRegistry rootServices) {
        this.includedBuildFactory = includedBuildFactory;
        this.projectRegistry = projectRegistry;
        this.dependencySubstitutionsBuilder = dependencySubstitutionsBuilder;
        this.gradleLauncherFactory = gradleLauncherFactory;
        this.listenerManager = listenerManager;
        this.rootServices = rootServices;
    }

    @Override
    public RootBuildState addRootBuild(BuildDefinition buildDefinition, BuildRequestContext requestContext) {
        if (rootBuild != null) {
            throw new IllegalStateException("Root build already defined.");
        }
        rootBuild = new DefaultRootBuildState(buildDefinition, requestContext, gradleLauncherFactory, listenerManager, rootServices);
        return rootBuild;
    }

    public boolean hasIncludedBuilds() {
        return !includedBuilds.isEmpty();
    }

    @Override
    public Collection<IncludedBuildState> getIncludedBuilds() {
        return includedBuilds.values();
    }

    @Override
    public IncludedBuildState addExplicitBuild(BuildDefinition buildDefinition, NestedBuildFactory nestedBuildFactory) {
        return registerBuild(buildDefinition, false, nestedBuildFactory);
    }

    @Override
    public IncludedBuildState getIncludedBuild(final BuildIdentifier buildIdentifier) {
        return CollectionUtils.findFirst(includedBuilds.values(), new Spec<IncludedBuildState>() {
            @Override
            public boolean isSatisfiedBy(IncludedBuildState includedBuild) {
                return includedBuild.getName().equals(buildIdentifier.getName());
            }
        });
    }

    @Override
    public void registerRootBuild(SettingsInternal settings) {
        validateIncludedBuilds(settings);

        rootBuild.setSettings(settings);
        projectRegistry.registerProjects(rootBuild);
        Collection<IncludedBuildState> includedBuilds = getIncludedBuilds();
        List<IncludedBuild> modelElements = new ArrayList<IncludedBuild>(includedBuilds.size());
        for (IncludedBuildState includedBuild : includedBuilds) {
            modelElements.add(includedBuild.getModel());
        }
        // Set the only visible included builds from the root build
        settings.getGradle().setIncludedBuilds(modelElements);
        registerProjects(includedBuilds);
        registerSubstitutions(includedBuilds);
    }

    private void registerProjects(Collection<IncludedBuildState> includedBuilds) {
        for (IncludedBuildState includedBuild : includedBuilds) {
            projectRegistry.registerProjects(includedBuild);
        }
    }

    private void validateIncludedBuilds(SettingsInternal settings) {
        Set<String> names = Sets.newHashSet();
        for (IncludedBuildState build : includedBuilds.values()) {
            String buildName = build.getName();
            if (!names.add(buildName)) {
                throw new GradleException("Included build '" + buildName + "' is not unique in composite.");
            }
            if (settings.getRootProject().getName().equals(buildName)) {
                throw new GradleException("Included build '" + buildName + "' collides with root project name.");
            }
            if (settings.findProject(":" + buildName) != null) {
                throw new GradleException("Included build '" + buildName + "' collides with subproject of the same name.");
            }
        }
    }

    private void registerSubstitutions(Iterable<IncludedBuildState> includedBuilds) {
        for (IncludedBuildState includedBuild : includedBuilds) {
            dependencySubstitutionsBuilder.build(includedBuild);
        }
    }

    @Override
    public IncludedBuildState addImplicitBuild(BuildDefinition buildDefinition, NestedBuildFactory nestedBuildFactory) {
        // TODO: synchronization
        IncludedBuildState includedBuild = includedBuilds.get(buildDefinition.getBuildRootDir());
        if (includedBuild == null) {
            includedBuild = registerBuild(buildDefinition, true, nestedBuildFactory);
            projectRegistry.registerProjects(includedBuild);
        }
        // TODO: else, verify that the build definition is the same
        return includedBuild;
    }

    @Override
    public NestedBuildState addNestedBuild(BuildDefinition buildDefinition, NestedBuildFactory nestedBuildFactory) {
        return new DefaultNestedBuild(buildDefinition, nestedBuildFactory, new BuildStateListener() {
            @Override
            public void projectsKnown(BuildState build1) {
                projectRegistry.registerProjects(build1);
            }
        });
    }

    private IncludedBuildState registerBuild(BuildDefinition buildDefinition, boolean isImplicit, NestedBuildFactory nestedBuildFactory) {
        // TODO: synchronization
        IncludedBuildState includedBuild = includedBuilds.get(buildDefinition.getBuildRootDir());
        if (includedBuild == null) {
            includedBuild = includedBuildFactory.createBuild(buildDefinition, isImplicit, nestedBuildFactory);
            includedBuilds.put(buildDefinition.getBuildRootDir(), includedBuild);
        }
        // TODO: else, verify that the build definition is the same
        return includedBuild;
    }

    @Override
    public void stop() {
        CompositeStoppable.stoppable(includedBuilds.values()).stop();
    }

}
