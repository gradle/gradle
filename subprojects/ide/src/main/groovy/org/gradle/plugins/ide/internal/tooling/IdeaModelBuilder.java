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
import org.gradle.api.Transformer;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.internal.jvm.Jvm;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.*;
import org.gradle.plugins.ide.internal.tooling.idea.*;
import org.gradle.plugins.ide.internal.tooling.java.DefaultJavaRuntime;
import org.gradle.tooling.internal.gradle.DefaultGradleModuleVersion;
import org.gradle.tooling.internal.gradle.DefaultGradleProject;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.io.File;
import java.util.*;

import static org.gradle.util.CollectionUtils.*;

public class IdeaModelBuilder implements ToolingModelBuilder {
    private final GradleProjectBuilder gradleProjectBuilder;

    private boolean offlineDependencyResolution;

    public IdeaModelBuilder(GradleProjectBuilder gradleProjectBuilder) {
        this.gradleProjectBuilder = gradleProjectBuilder;
    }

    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.idea.IdeaProject");
    }

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
        root.getPlugins().getPlugin(IdeaPlugin.class).makeSureModuleNamesAreUnique();
    }

    private DefaultIdeaProject build(Project project, DefaultGradleProject rootGradleProject) {
        IdeaModel ideaModel = project.getPlugins().getPlugin(IdeaPlugin.class).getModel();
        IdeaProject projectModel = ideaModel.getProject();
        JavaVersion projectSourceLanguageLevel = convertIdeaLanguageLevelToJavaVersion(projectModel.getLanguageLevel());

        final Jvm currentJvm = Jvm.current();
        final DefaultJavaRuntime javaRuntime = new DefaultJavaRuntime(currentJvm.getJavaHome(), currentJvm.getJavaVersion());

        DefaultIdeaProject out = new DefaultIdeaProject()
            .setName(projectModel.getName())
            .setJdkName(projectModel.getJdkName())
            .setLanguageLevel(new DefaultIdeaLanguageLevel(projectModel.getLanguageLevel().getLevel()))
            .setJavaSourceSettings(new DefaultIdeaJavaSettings()
                .setSourceLanguageLevel(projectSourceLanguageLevel)
                .setTargetRuntime(javaRuntime));

        Map<String, DefaultIdeaModule> modules = new HashMap<String, DefaultIdeaModule>();
        for (IdeaModule module : projectModel.getModules()) {
            appendModule(modules, module, out, rootGradleProject, javaRuntime);
        }
        for (IdeaModule module : projectModel.getModules()) {
            buildDependencies(modules, module);
        }
        final Collection<DefaultIdeaModule> ideaModules = modules.values();

        configureTargetBytecodeLevelInherited(out, ideaModules);

        out.setChildren(new LinkedList<DefaultIdeaModule>(ideaModules));
        return out;
    }

    private void configureTargetBytecodeLevelInherited(DefaultIdeaProject out, Collection<DefaultIdeaModule> ideaModules) {
        final Set<JavaVersion> moduleBytecodeVersions = toSet(compact(collect(ideaModules, new Transformer<JavaVersion, DefaultIdeaModule>() {
            @Override
            public JavaVersion transform(DefaultIdeaModule defaultIdeaModule) {
                final DefaultIdeaJavaSettings javaSourceSettings = defaultIdeaModule.getJavaSourceSettings();
                return javaSourceSettings == null ? null : javaSourceSettings.getTargetBytecodeLevel();
            }
        })));

        if (moduleBytecodeVersions.size() > 0) {
            final JavaVersion maxBytecodeLevel = Collections.max(moduleBytecodeVersions);
            out.getJavaSourceSettings().setTargetBytecodeLevel(maxBytecodeLevel);
            for (DefaultIdeaModule ideaModule : ideaModules) {
                if (moduleTargetByteIsMaxBytecodeLevel(maxBytecodeLevel, ideaModule)) {
                    ideaModule.getJavaSourceSettings().setTargetBytecodeLevelInherited(true);
                }
            }
            out.getJavaSourceSettings().setTargetBytecodeLevel(maxBytecodeLevel);
        }
    }

    private boolean moduleTargetByteIsMaxBytecodeLevel(JavaVersion maxBytecodeLevel, DefaultIdeaModule ideaModule) {
        final DefaultIdeaJavaSettings moduleJavaSourceSettings = ideaModule.getJavaSourceSettings();
        if(moduleJavaSourceSettings == null) {
            return false;
        }
        return moduleJavaSourceSettings.getTargetBytecodeLevel() != null && moduleJavaSourceSettings.getTargetBytecodeLevel().equals(maxBytecodeLevel);
    }

    private void buildDependencies(Map<String, DefaultIdeaModule> modules, IdeaModule ideaModule) {
        ideaModule.setOffline(offlineDependencyResolution);
        Set<Dependency> resolved = ideaModule.resolveDependencies();
        List<DefaultIdeaDependency> dependencies = new LinkedList<DefaultIdeaDependency>();
        for (Dependency dependency : resolved) {
            if (dependency instanceof SingleEntryModuleLibrary) {
                SingleEntryModuleLibrary d = (SingleEntryModuleLibrary) dependency;
                DefaultIdeaSingleEntryLibraryDependency defaultDependency = new org.gradle.tooling.internal.idea.DefaultIdeaSingleEntryLibraryDependency()
                    .setFile(d.getLibraryFile())
                    .setSource(d.getSourceFile())
                    .setJavadoc(d.getJavadocFile())
                    .setScope(new DefaultIdeaDependencyScope(d.getScope()))
                    .setExported(d.getExported());

                if (d.getModuleVersion() != null) {
                    defaultDependency.setGradleModuleVersion(new DefaultGradleModuleVersion(d.getModuleVersion()));
                }
                dependencies.add(defaultDependency);
            } else if (dependency instanceof ModuleDependency) {
                ModuleDependency d = (ModuleDependency) dependency;
                DefaultIdeaModuleDependency defaultDependency = new org.gradle.tooling.internal.idea.DefaultIdeaModuleDependency()
                    .setExported(d.getExported())
                    .setScope(new DefaultIdeaDependencyScope(d.getScope()))
                    .setDependencyModule(modules.get(d.getName()));
                dependencies.add(defaultDependency);
            }
        }
        modules.get(ideaModule.getName()).setDependencies(dependencies);
    }

    private void appendModule(Map<String, DefaultIdeaModule> modules, IdeaModule ideaModule, DefaultIdeaProject ideaProject, DefaultGradleProject rootGradleProject, DefaultJavaRuntime javaRuntime) {
        DefaultIdeaContentRoot contentRoot = new DefaultIdeaContentRoot()
            .setRootDirectory(ideaModule.getContentRoot())
            .setSourceDirectories(srcDirs(ideaModule.getSourceDirs(), ideaModule.getGeneratedSourceDirs()))
            .setTestDirectories(srcDirs(ideaModule.getTestSourceDirs(), ideaModule.getGeneratedSourceDirs()))
            .setExcludeDirectories(ideaModule.getExcludeDirs());

        Project project = ideaModule.getProject();
        JavaPluginConvention javaPluginConvention = project.getConvention().findPlugin(JavaPluginConvention.class);

        DefaultIdeaModule defaultIdeaModule = new DefaultIdeaModule()
            .setName(ideaModule.getName())
            .setParent(ideaProject)
            .setGradleProject(rootGradleProject.findByPath(ideaModule.getProject().getPath()))
            .setContentRoots(Collections.singletonList(contentRoot))
            .setCompilerOutput(new DefaultIdeaCompilerOutput()
                .setInheritOutputDirs(ideaModule.getInheritOutputDirs() != null ? ideaModule.getInheritOutputDirs() : false)
                .setOutputDir(ideaModule.getOutputDir())
                .setTestOutputDir(ideaModule.getTestOutputDir()));
        if (javaPluginConvention != null) {
            final IdeaLanguageLevel ideaModuleLanguageLevel = ideaModule.getLanguageLevel();
            JavaVersion moduleSourceLanguageLevel = convertIdeaLanguageLevelToJavaVersion(ideaModuleLanguageLevel);
            JavaVersion moduleTargetLanguageLevel = javaPluginConvention.getTargetCompatibility();
            defaultIdeaModule.setJavaSourceSettings(new DefaultIdeaJavaSettings()
                .setSourceLanguageLevel(moduleSourceLanguageLevel)
                .setTargetBytecodeLevel(moduleTargetLanguageLevel)
                .setTargetRuntime(javaRuntime)
                .setTargetRuntimeInherited(true));
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
