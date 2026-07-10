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

package org.gradle.plugins.ide.internal.tooling;

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.composite.IncludedBuildInternal;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.Dependency;
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.plugins.ide.idea.model.IdeaProject;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaContentRoot;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaDependency;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaJavaLanguageSettings;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaLanguageLevel;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaModule;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaProject;
import org.gradle.plugins.ide.internal.tooling.java.DefaultInstalledJdk;
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleProject;
import org.gradle.util.Path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Builds the {@link org.gradle.tooling.model.idea.IdeaProject} model
 * that contains project Java language settings and a flat list of Idea modules.
 */
public class IdeaModelBuilder implements IdeaModelBuilderInternal {

    private static final String MODEL_NAME = org.gradle.tooling.model.idea.IdeaProject.class.getName();

    private final GradleProjectBuilderInternal gradleProjectBuilder;

    public IdeaModelBuilder(GradleProjectBuilderInternal gradleProjectBuilder) {
        this.gradleProjectBuilder = gradleProjectBuilder;
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals(MODEL_NAME);
    }

    @Override
    public DefaultIdeaProject buildAll(String modelName, Project project) {
        return buildForRoot(project, false);
    }

    @Override
    public DefaultIdeaProject buildForRoot(Project project, boolean offlineDependencyResolution) {
        Project root = project.getRootProject();
        applyIdeaPluginToBuildTree(((ProjectInternal) root).getOwner(), new HashSet<>());
        DefaultGradleProject rootGradleProject = gradleProjectBuilder.buildForRoot(project);
        return build(root, rootGradleProject, offlineDependencyResolution);
    }

    private static void applyIdeaPluginToBuildTree(ProjectState rootState, Set<Path> alreadyProcessed) {
        BuildState build = rootState.getOwner();
        build.getProjects().applyToMutableStateOfAllProjects(access -> {
            for (Project p : access.getMutableModel(rootState).getAllprojects()) {
                p.getPluginManager().apply(IdeaPlugin.class);
            }
        });
        for (IncludedBuildInternal reference : build.getMutableModel().includedBuilds()) {
            BuildState target = reference.getTarget();
            if (target instanceof IncludedBuildState) {
                target.ensureProjectsConfigured();
                if (alreadyProcessed.add(target.getIdentityPath())) {
                    applyIdeaPluginToBuildTree(target.getProjects().getRootProject(), alreadyProcessed);
                }
            }
        }
    }

    private DefaultIdeaProject build(Project project, DefaultGradleProject rootGradleProject, boolean offlineDependencyResolution) {
        IdeaModel ideaModel = ideaPluginFor(project).getModel();
        IdeaProject projectModel = ideaModel.getProject();
        JavaVersion projectSourceLanguageLevel = IdeaModuleBuilderSupport.convertToJavaVersion(projectModel.getLanguageLevel());
        JavaVersion projectTargetBytecodeLevel = projectModel.getTargetBytecodeVersion();

        DefaultIdeaProject out = new DefaultIdeaProject()
            .setName(projectModel.getName())
            .setJdkName(projectModel.getJdkName())
            .setLanguageLevel(new DefaultIdeaLanguageLevel(projectModel.getLanguageLevel().getLevel()))
            .setJavaLanguageSettings(new DefaultIdeaJavaLanguageSettings()
                .setSourceLanguageLevel(projectSourceLanguageLevel)
                .setTargetBytecodeVersion(projectTargetBytecodeLevel)
                .setJdk(DefaultInstalledJdk.current()));

        List<DefaultIdeaModule> ideaModules = new ArrayList<>();
        for (IdeaModule module : projectModel.getModules()) {
            ideaModules.add(createModule(module, out, rootGradleProject, offlineDependencyResolution));
        }
        out.setChildren(new LinkedList<>(ideaModules));
        return out;
    }

    private IdeaPlugin ideaPluginFor(Project project) {
        return project.getPlugins().getPlugin(IdeaPlugin.class);
    }

    private static void buildDependencies(DefaultIdeaModule tapiModule, IdeaModule ideaModule, boolean offlineDependencyResolution) {
        ideaModule.setOffline(offlineDependencyResolution);
        Set<Dependency> resolved = ideaModule.resolveDependencies();
        List<DefaultIdeaDependency> dependencies = IdeaModuleBuilderSupport.buildDependencies(resolved);
        tapiModule.setDependencies(dependencies);
    }

    private static DefaultIdeaModule createModule(
        IdeaModule ideaModule,
        DefaultIdeaProject ideaProject,
        DefaultGradleProject rootGradleProject,
        boolean offlineDependencyResolution
    ) {
        DefaultIdeaContentRoot contentRoot = IdeaModuleBuilderSupport.buildContentRoot(ideaModule);
        Project project = ideaModule.getProject();

        DefaultIdeaModule defaultIdeaModule = new DefaultIdeaModule()
            .setName(ideaModule.getName())
            .setParent(ideaProject)
            .setGradleProject(rootGradleProject.findByPath(ideaModule.getProject().getPath()))
            .setContentRoots(Collections.singletonList(contentRoot))
            .setJdkName(ideaModule.getJdkName())
            .setCompilerOutput(IdeaModuleBuilderSupport.buildCompilerOutput(ideaModule));

        JavaPluginExtension javaPluginExtension = project.getExtensions().findByType(JavaPluginExtension.class);
        if (javaPluginExtension != null) {
            final IdeaLanguageLevel ideaModuleLanguageLevel = ideaModule.getLanguageLevel();
            JavaVersion moduleSourceLanguageLevel = IdeaModuleBuilderSupport.convertToJavaVersion(ideaModuleLanguageLevel);
            JavaVersion moduleTargetBytecodeVersion = ideaModule.getTargetBytecodeVersion();
            defaultIdeaModule.setJavaLanguageSettings(new DefaultIdeaJavaLanguageSettings()
                .setSourceLanguageLevel(moduleSourceLanguageLevel)
                .setTargetBytecodeVersion(moduleTargetBytecodeVersion));
        }

        buildDependencies(defaultIdeaModule, ideaModule, offlineDependencyResolution);

        return defaultIdeaModule;
    }

}
