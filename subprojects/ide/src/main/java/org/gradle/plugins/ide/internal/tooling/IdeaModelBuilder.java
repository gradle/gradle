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
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.composite.internal.IncludedBuildInternal;
import org.gradle.internal.service.ServiceRegistry;
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
import org.gradle.tooling.internal.gradle.DefaultGradleModuleVersion;
import org.gradle.tooling.internal.gradle.DefaultGradleProject;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IdeaModelBuilder implements ToolingModelBuilder {
    private final GradleProjectBuilder gradleProjectBuilder;

    private boolean offlineDependencyResolution;

    public IdeaModelBuilder(GradleProjectBuilder gradleProjectBuilder, ServiceRegistry services) {
        this.gradleProjectBuilder = gradleProjectBuilder;
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.idea.IdeaProject");
    }

    @Override
    public DefaultIdeaProject buildAll(String modelName, Project project) {
        Project root = project.getRootProject();
        applyIdeaPlugin(root);
        DefaultGradleProject<?> rootGradleProject = gradleProjectBuilder.buildAll(project);
        return build(root, rootGradleProject);
    }

    private void applyIdeaPlugin(Project root) {
        Set<Project> allProjects = root.getAllprojects();
        for (Project p : allProjects) {
            p.getPluginManager().apply(IdeaPlugin.class);
        }
        for (IncludedBuild includedBuild : root.getGradle().getIncludedBuilds()) {
            IncludedBuildInternal includedBuildInternal = (IncludedBuildInternal) includedBuild;
            applyIdeaPlugin(includedBuildInternal.getConfiguredBuild().getRootProject());
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

        Map<String, DefaultIdeaModule> modules = new LinkedHashMap<String, DefaultIdeaModule>();
        for (IdeaModule module : projectModel.getModules()) {
            appendModule(modules, module, out, rootGradleProject);
        }
        for (IdeaModule module : projectModel.getModules()) {
            buildDependencies(modules, module);
        }
        final Collection<DefaultIdeaModule> ideaModules = modules.values();
        out.setChildren(new LinkedList<DefaultIdeaModule>(ideaModules));
        return out;
    }

    private IdeaPlugin ideaPluginFor(Project project) {
        return project.getPlugins().getPlugin(IdeaPlugin.class);
    }

    private void buildDependencies(Map<String, DefaultIdeaModule> modules, IdeaModule ideaModule) {
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

                // Find IdeaModule model for dependency within same build: may be null
                DefaultIdeaModule targetModule = modules.get(moduleDependency.getName());
                ideaModuleDependency.setDependencyModule(targetModule);

                dependencies.add(ideaModuleDependency);
            }
        }
        modules.get(ideaModule.getName()).setDependencies(dependencies);
    }

    private void appendModule(Map<String, DefaultIdeaModule> modules, IdeaModule ideaModule, DefaultIdeaProject ideaProject, DefaultGradleProject rootGradleProject) {
        DefaultIdeaContentRoot contentRoot = new DefaultIdeaContentRoot()
            .setRootDirectory(ideaModule.getContentRoot())
            .setSourceDirectories(srcDirs(ideaModule.getSourceDirs(), ideaModule.getGeneratedSourceDirs()))
            .setTestDirectories(srcDirs(ideaModule.getTestSourceDirs(), ideaModule.getGeneratedSourceDirs()))
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
        JavaPluginConvention javaPluginConvention = project.getConvention().findPlugin(JavaPluginConvention.class);
        if (javaPluginConvention != null) {
            final IdeaLanguageLevel ideaModuleLanguageLevel = ideaModule.getLanguageLevel();
            JavaVersion moduleSourceLanguageLevel = convertIdeaLanguageLevelToJavaVersion(ideaModuleLanguageLevel);
            JavaVersion moduleTargetBytecodeVersion = ideaModule.getTargetBytecodeVersion();
            defaultIdeaModule.setJavaLanguageSettings(new DefaultIdeaJavaLanguageSettings()
                .setSourceLanguageLevel(moduleSourceLanguageLevel)
                .setTargetBytecodeVersion(moduleTargetBytecodeVersion));
        }

        modules.put(ideaModule.getName(), defaultIdeaModule);
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
