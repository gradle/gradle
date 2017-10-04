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
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.initialization.ConfigurableIncludedBuild;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.artifacts.component.DefaultBuildIdentifier;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.specs.Spec;
import org.gradle.initialization.DefaultProjectDescriptor;
import org.gradle.initialization.NestedBuildFactory;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.util.CollectionUtils;
import org.gradle.util.Path;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class DefaultIncludedBuildRegistry implements IncludedBuildRegistry, Stoppable {
    private final IncludedBuildFactory includedBuildFactory;
    private final DefaultProjectPathRegistry projectRegistry;
    private final IncludedBuildDependencySubstitutionsBuilder dependencySubstitutionsBuilder;

    // TODO: Locking around this
    private final Map<File, IncludedBuildInternal> includedBuilds = Maps.newLinkedHashMap();


    public DefaultIncludedBuildRegistry(IncludedBuildFactory includedBuildFactory, DefaultProjectPathRegistry projectRegistry, IncludedBuildDependencySubstitutionsBuilder dependencySubstitutionsBuilder, CompositeBuildContext compositeBuildContext) {
        this.includedBuildFactory = includedBuildFactory;
        this.projectRegistry = projectRegistry;
        this.dependencySubstitutionsBuilder = dependencySubstitutionsBuilder;
        compositeBuildContext.setIncludedBuildRegistry(this);
    }

    @Override
    public boolean hasIncludedBuilds() {
        return !includedBuilds.isEmpty();
    }

    @Override
    public Map<File, IncludedBuild> getIncludedBuilds() {
        return Collections.<File, IncludedBuild>unmodifiableMap(includedBuilds);
    }

    @Override
    public IncludedBuildInternal addExplicitBuild(File buildDirectory, NestedBuildFactory nestedBuildFactory) {
        return registerBuild(buildDirectory, nestedBuildFactory);
    }

    @Override
    public IncludedBuild getBuild(final BuildIdentifier buildIdentifier) {
        return CollectionUtils.findFirst(includedBuilds.values(), new Spec<IncludedBuild>() {
            @Override
            public boolean isSatisfiedBy(IncludedBuild includedBuild) {
                return includedBuild.getName().equals(buildIdentifier.getName());
            }
        });
    }

    @Override
    public void validateExplicitIncludedBuilds(SettingsInternal settings) {
        validateIncludedBuilds(settings);

        registerRootBuildProjects(settings);
        Collection<IncludedBuild> includedBuilds = getIncludedBuilds().values();
        // Set the only visible included builds from the root build
        settings.getGradle().setIncludedBuilds(includedBuilds);
        registerProjects(includedBuilds);
        registerSubstitutions(includedBuilds);
    }

    private void validateIncludedBuilds(SettingsInternal settings) {
        Set<String> names = Sets.newHashSet();
        for (IncludedBuild build : includedBuilds.values()) {
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

    private void registerSubstitutions(Iterable<IncludedBuild> includedBuilds) {
        for (IncludedBuild includedBuild : includedBuilds) {
            dependencySubstitutionsBuilder.build((IncludedBuildInternal)includedBuild);
        }
    }

    @Override
    public ConfigurableIncludedBuild addImplicitBuild(File buildDirectory, NestedBuildFactory nestedBuildFactory) {
        ConfigurableIncludedBuild includedBuild = includedBuilds.get(buildDirectory);
        if (includedBuild == null) {
            includedBuild = registerBuild(buildDirectory, nestedBuildFactory);
            registerProjects(Collections.<IncludedBuild>singletonList(includedBuild));
        }
        return includedBuild;
    }

    private IncludedBuildInternal registerBuild(File buildDirectory, NestedBuildFactory nestedBuildFactory) {
        // TODO: synchronization
        IncludedBuildInternal includedBuild = includedBuilds.get(buildDirectory);
        if (includedBuild == null) {
            includedBuild = includedBuildFactory.createBuild(buildDirectory, nestedBuildFactory);
            includedBuilds.put(buildDirectory, includedBuild);
        }
        return includedBuild;
    }

    private void registerRootBuildProjects(SettingsInternal settings) {
        ProjectRegistry<DefaultProjectDescriptor> settingsProjectRegistry = settings.getProjectRegistry();
        String rootName = settingsProjectRegistry.getRootProject().getName();
        DefaultBuildIdentifier buildIdentifier = new DefaultBuildIdentifier(rootName, true);
        registerProjects(Path.ROOT, buildIdentifier, settingsProjectRegistry.getAllProjects());
    }

    private void registerProjects(Iterable<IncludedBuild> includedBuilds) {
        for (IncludedBuild includedBuild : includedBuilds) {
            Path rootProjectPath = Path.ROOT.child(includedBuild.getName());
            BuildIdentifier buildIdentifier = new DefaultBuildIdentifier(includedBuild.getName());
            Set<DefaultProjectDescriptor> allProjects = ((IncludedBuildInternal) includedBuild).getLoadedSettings().getProjectRegistry().getAllProjects();
            registerProjects(rootProjectPath, buildIdentifier, allProjects);
        }
    }

    private void registerProjects(Path rootPath, BuildIdentifier buildIdentifier, Set<DefaultProjectDescriptor> allProjects) {
        for (DefaultProjectDescriptor project : allProjects) {
            Path projectIdentityPath = rootPath.append(project.path());
            ProjectComponentIdentifier projectComponentIdentifier = DefaultProjectComponentIdentifier.newProjectId(buildIdentifier, project.getPath());
            projectRegistry.add(projectIdentityPath, projectComponentIdentifier);
        }
    }

    @Override
    public void stop() {
        CompositeStoppable.stoppable(includedBuilds.values()).stop();
    }
}
