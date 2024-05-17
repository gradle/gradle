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

import com.google.common.collect.Lists;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
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
import org.gradle.plugins.ide.idea.model.ModuleDependency;
import org.gradle.plugins.ide.idea.model.SingleEntryModuleLibrary;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaCompilerOutput;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaContentRoot;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaDependency;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaDependencyScope;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaJavaLanguageSettings;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaLanguageLevel;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaModule;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaModuleDependency;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaProject;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaSingleEntryLibraryDependency;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaSourceDirectory;
import org.gradle.plugins.ide.internal.tooling.java.DefaultInstalledJdk;
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleModuleVersion;
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleProject;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class IdeaModelBuilder implements ToolingModelBuilder {
    private final GradleProjectBuilderInternal gradleProjectBuilder;

    private boolean offlineDependencyResolution;

    public IdeaModelBuilder(GradleProjectBuilderInternal gradleProjectBuilder) {
        this.gradleProjectBuilder = gradleProjectBuilder;
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.idea.IdeaProject");
    }

    @Override
    public DefaultIdeaProject buildAll(String modelName, Project project) {
        Project root = project.getRootProject();
        applyIdeaPlugin((ProjectInternal) root, new ArrayList<>());
        DefaultGradleProject rootGradleProject = gradleProjectBuilder.buildForRoot(project);
        return build(root, rootGradleProject);
    }

    private void applyIdeaPlugin(ProjectInternal root, List<GradleInternal> alreadyProcessed) {
        Set<Project> allProjects = root.getAllprojects();
        for (Project p : allProjects) {
            p.getPluginManager().apply(IdeaPlugin.class);
        }
        for (IncludedBuildInternal reference : root.getGradle().includedBuilds()) {
            BuildState target = reference.getTarget();
            if (target instanceof IncludedBuildState) {
                target.ensureProjectsConfigured();
                GradleInternal build = target.getMutableModel();
                if (!alreadyProcessed.contains(build)) {
                    alreadyProcessed.add(build);
                    applyIdeaPlugin(build.getRootProject(), alreadyProcessed);
                }
            }
        }
    }

    private DefaultIdeaProject build(Project project, DefaultGradleProject rootGradleProject) {
        IdeaModel ideaModel = ideaPluginFor(project).getModel();
        IdeaProject projectModel = ideaModel.getProject();
        JavaVersion projectSourceLanguageLevel = convertIdeaLanguageLevelToJavaVersion(projectModel.getLanguageLevel());
        JavaVersion projectTargetBytecodeLevel = projectModel.getTargetBytecodeVersion();

        DefaultIdeaProject out = new DefaultIdeaProject()
            .setName(projectModel.getName())
            .setJdkName(projectModel.getJdkName())
            .setLanguageLevel(new DefaultIdeaLanguageLevel(projectModel.getLanguageLevel().getLevel()))
            .setJavaLanguageSettings(new DefaultIdeaJavaLanguageSettings()
                .setSourceLanguageLevel(projectSourceLanguageLevel)
                .setTargetBytecodeVersion(projectTargetBytecodeLevel)
                .setJdk(DefaultInstalledJdk.current()));

        List<DefaultIdeaModule> ideaModules = Lists.newArrayList();
        for (IdeaModule module : projectModel.getModules()) {
            ideaModules.add(createModule(module, out, rootGradleProject));
        }
        out.setChildren(new LinkedList<>(ideaModules));
        return out;
    }

    private IdeaPlugin ideaPluginFor(Project project) {
        return project.getPlugins().getPlugin(IdeaPlugin.class);
    }

    private void buildDependencies(DefaultIdeaModule tapiModule, IdeaModule ideaModule) {
        ideaModule.setOffline(offlineDependencyResolution);
        Set<Dependency> resolved = ideaModule.resolveDependencies();
        List<DefaultIdeaDependency> dependencies = new LinkedList<DefaultIdeaDependency>();
        for (Dependency dependency : resolved) {
            if (dependency instanceof SingleEntryModuleLibrary) {
                SingleEntryModuleLibrary d = (SingleEntryModuleLibrary) dependency;
                DefaultIdeaSingleEntryLibraryDependency defaultDependency = new DefaultIdeaSingleEntryLibraryDependency()
                    .setFile(d.getLibraryFile())
                    .setSource(d.getSourceFile())
                    .setJavadoc(d.getJavadocFile())
                    .setScope(new DefaultIdeaDependencyScope(d.getScope()))
                    .setExported(d.isExported());

                if (d.getModuleVersion() != null) {
                    defaultDependency.setGradleModuleVersion(new DefaultGradleModuleVersion(d.getModuleVersion()));
                }
                dependencies.add(defaultDependency);
            } else if (dependency instanceof ModuleDependency) {
                ModuleDependency moduleDependency = (ModuleDependency) dependency;

                DefaultIdeaModuleDependency ideaModuleDependency = new DefaultIdeaModuleDependency(moduleDependency.getName())
                    .setExported(moduleDependency.isExported())
                    .setScope(new DefaultIdeaDependencyScope(moduleDependency.getScope()));

                dependencies.add(ideaModuleDependency);
            }
        }
        tapiModule.setDependencies(dependencies);
    }

    private DefaultIdeaModule createModule(IdeaModule ideaModule, DefaultIdeaProject ideaProject, DefaultGradleProject rootGradleProject) {
        DefaultIdeaContentRoot contentRoot = new DefaultIdeaContentRoot()
            .setRootDirectory(ideaModule.getContentRoot())
            .setSourceDirectories(srcDirs(ideaModule.getSourceDirs(), ideaModule.getGeneratedSourceDirs()))
            .setTestDirectories(srcDirs(ideaModule.getTestSources().getFiles(), ideaModule.getGeneratedSourceDirs()))
            .setResourceDirectories(srcDirs(ideaModule.getResourceDirs(), ideaModule.getGeneratedSourceDirs()))
            .setTestResourceDirectories(srcDirs(ideaModule.getTestResources().getFiles(), ideaModule.getGeneratedSourceDirs()))
            .setExcludeDirectories(ideaModule.getExcludeDirs());

        Project project = ideaModule.getProject();

        DefaultIdeaModule defaultIdeaModule = new DefaultIdeaModule()
            .setName(ideaModule.getName())
            .setParent(ideaProject)
            .setGradleProject(rootGradleProject.findByPath(ideaModule.getProject().getPath()))
            .setContentRoots(Collections.singletonList(contentRoot))
            .setJdkName(ideaModule.getJdkName())
            .setCompilerOutput(new DefaultIdeaCompilerOutput()
                .setInheritOutputDirs(ideaModule.getInheritOutputDirs() != null ? ideaModule.getInheritOutputDirs() : false)
                .setOutputDir(ideaModule.getOutputDir())
                .setTestOutputDir(ideaModule.getTestOutputDir()));
        JavaPluginExtension javaPluginExtension = project.getExtensions().findByType(JavaPluginExtension.class);
        if (javaPluginExtension != null) {
            final IdeaLanguageLevel ideaModuleLanguageLevel = ideaModule.getLanguageLevel();
            JavaVersion moduleSourceLanguageLevel = convertIdeaLanguageLevelToJavaVersion(ideaModuleLanguageLevel);
            JavaVersion moduleTargetBytecodeVersion = ideaModule.getTargetBytecodeVersion();
            defaultIdeaModule.setJavaLanguageSettings(new DefaultIdeaJavaLanguageSettings()
                .setSourceLanguageLevel(moduleSourceLanguageLevel)
                .setTargetBytecodeVersion(moduleTargetBytecodeVersion));
        }
        buildDependencies(defaultIdeaModule, ideaModule);

        return defaultIdeaModule;
    }

    private Set<DefaultIdeaSourceDirectory> srcDirs(Set<File> sourceDirs, Set<File> generatedSourceDirs) {
        Set<DefaultIdeaSourceDirectory> out = new LinkedHashSet<DefaultIdeaSourceDirectory>();
        for (File s : sourceDirs) {
            DefaultIdeaSourceDirectory sourceDirectory = new DefaultIdeaSourceDirectory().setDirectory(s);
            if (generatedSourceDirs.contains(s)) {
                sourceDirectory.setGenerated(true);
            }
            out.add(sourceDirectory);
        }
        return out;
    }

    public IdeaModelBuilder setOfflineDependencyResolution(boolean offlineDependencyResolution) {
        this.offlineDependencyResolution = offlineDependencyResolution;
        return this;
    }

    private JavaVersion convertIdeaLanguageLevelToJavaVersion(IdeaLanguageLevel ideaLanguageLevel) {
        if (ideaLanguageLevel == null) {
            return null;
        }
        String languageLevel = ideaLanguageLevel.getLevel();
        return JavaVersion.valueOf(languageLevel.replaceFirst("JDK", "VERSION"));
    }
}
