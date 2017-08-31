/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.artifacts.component.DefaultBuildIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionsInternal;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.DefaultProjectDescriptor;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;
import org.gradle.internal.composite.CompositeContextBuilder;
import org.gradle.util.Path;

import java.util.Set;

public class DefaultCompositeContextBuilder implements CompositeContextBuilder {
    private static final org.gradle.api.logging.Logger LOGGER = Logging.getLogger(DefaultCompositeContextBuilder.class);
    private final DefaultIncludedBuilds allIncludedBuilds;
    private final DefaultProjectPathRegistry projectRegistry;
    private final CompositeBuildContext context;

    public DefaultCompositeContextBuilder(DefaultIncludedBuilds allIncludedBuilds, DefaultProjectPathRegistry projectRegistry, CompositeBuildContext context) {
        this.allIncludedBuilds = allIncludedBuilds;
        this.projectRegistry = projectRegistry;
        this.context = context;
    }

    @Override
    public void setRootBuild(SettingsInternal settings) {
        ProjectRegistry<DefaultProjectDescriptor> settingsProjectRegistry = settings.getProjectRegistry();
        String rootName = settingsProjectRegistry.getRootProject().getName();
        DefaultBuildIdentifier buildIdentifier = new DefaultBuildIdentifier(rootName, true);
        registerProjects(Path.ROOT, buildIdentifier, settingsProjectRegistry.getAllProjects());
    }

    @Override
    public void addIncludedBuilds(Iterable<IncludedBuild> includedBuilds) {
        registerProjects(includedBuilds);
        registerSubstitutions(includedBuilds);
    }

    private void registerProjects(Iterable<IncludedBuild> includedBuilds) {
        for (IncludedBuild includedBuild : includedBuilds) {
            allIncludedBuilds.registerBuild(includedBuild);
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

    private void registerSubstitutions(Iterable<IncludedBuild> includedBuilds) {
        IncludedBuildDependencySubstitutionsBuilder contextBuilder = new IncludedBuildDependencySubstitutionsBuilder(context);
        for (IncludedBuild includedBuild : includedBuilds) {
            doAddToCompositeContext((IncludedBuildInternal) includedBuild, contextBuilder);
        }
    }

    private void doAddToCompositeContext(IncludedBuildInternal build, IncludedBuildDependencySubstitutionsBuilder contextBuilder) {
        DependencySubstitutionsInternal substitutions = build.resolveDependencySubstitutions();
        if (!substitutions.hasRules()) {
            // Configure the included build to discover substitutions
            LOGGER.info("[composite-build] Configuring build: " + build.getProjectDir());
            contextBuilder.build(build);
        } else {
            // Register the defined substitutions for included build
            context.registerSubstitution(substitutions.getRuleAction());
        }
    }
}
