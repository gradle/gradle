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

package org.gradle.tooling.internal.provider;

import org.gradle.api.Project;
import org.gradle.api.internal.GradleInternal;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.*;
import org.gradle.tooling.internal.idea.*;
import org.gradle.tooling.internal.protocol.InternalIdeaProject;
import org.gradle.tooling.internal.protocol.ProjectVersion3;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaSourceDirectory;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.codehaus.groovy.runtime.InvokerHelper.asList;

/**
 * @author: Szczepan Faber, created at: 7/23/11
 */
public class IdeaModelBuilder implements BuildsModel {
    public boolean canBuild(Class type) {
        return type == InternalIdeaProject.class;
    }

    private final TasksFactory tasksFactory;

    public IdeaModelBuilder() {
        this.tasksFactory = new TasksFactory(true);
    }

    public ProjectVersion3 buildAll(GradleInternal gradle) {
        Project root = gradle.getRootProject();
        tasksFactory.collectTasks(root);
        new IdeaPluginApplier().apply(root);
        return build(root);
    }

    private ProjectVersion3 build(Project project) {
        IdeaModel ideaModel = project.getPlugins().getPlugin(IdeaPlugin.class).getModel();
        IdeaProject projectModel = ideaModel.getProject();

        DefaultIdeaProject out = new DefaultIdeaProject()
                .setName(projectModel.getName())
                .setId(project.getPath())
                .setJdkName(projectModel.getJdkName())
                .setLanguageLevel(projectModel.getLanguageLevel().getFormatted());

        List<DefaultIdeaModule> modules = new LinkedList<DefaultIdeaModule>();
        for (IdeaModule module : projectModel.getModules()) {
            appendModule(modules, module, out);
        }
        out.setChildren(modules);

        return out;
    }

    private void appendModule(Collection<DefaultIdeaModule> modules, IdeaModule ideaModule, DefaultIdeaProject ideaProject) {
        DefaultIdeaModule defaultIdeaModule = new DefaultIdeaModule()
                .setName(ideaModule.getName())
                .setContentRoots(asList(ideaModule.getContentRoot()))
                .setParent(ideaProject)
                .setInheritOutputDirs(ideaModule.getInheritOutputDirs() != null ? ideaModule.getInheritOutputDirs() : false)
                .setOutputDir(ideaModule.getOutputDir())
                .setTestOutputDir(ideaModule.getTestOutputDir())
                .setModuleFileDir(ideaModule.getIml().getGenerateTo())
                .setSourceDirectories(srcDirs(ideaModule.getSourceDirs()))
                .setTestDirectories(srcDirs(ideaModule.getTestSourceDirs()))
                .setExcludeDirectories(new LinkedList<File>(ideaModule.getExcludeDirs()));

        List<IdeaDependency> deps = new LinkedList<IdeaDependency>();
        defaultIdeaModule.setDependencies(deps);

        Set<Dependency> resolved = ideaModule.resolveDependencies();
        List<IdeaDependency> dependencies = new LinkedList<IdeaDependency>();
        for (Dependency dependency : resolved) {
            if (dependency instanceof SingleEntryModuleLibrary) {
                SingleEntryModuleLibrary d = (SingleEntryModuleLibrary) dependency;
                IdeaDependency defaultDependency = new DefaultIdeaLibraryDependency()
                        .setFile(d.getLibraryFile())
                        .setSource(d.getSourceFile())
                        .setJavadoc(d.getJavadocFile())
                        .setScope(d.getScope())
                        .setExported(d.getExported());
                dependencies.add(defaultDependency);
            } else if (dependency instanceof ModuleDependency) {
                ModuleDependency d = (ModuleDependency) dependency;
                IdeaDependency defaultDependency = new DefaultIdeaModuleDependency()
                        .setExported(d.getExported())
                        .setScope(d.getScope())
                        .setDependencyModuleName(d.getName());
                dependencies.add(defaultDependency);
            }
        }
        defaultIdeaModule.setDependencies(dependencies);

        modules.add(defaultIdeaModule);
    }

    private List<IdeaSourceDirectory> srcDirs(Set<File> sourceDirs) {
        List<IdeaSourceDirectory> out = new LinkedList<IdeaSourceDirectory>();
        for (File s : sourceDirs) {
            out.add(new DefaultIdeaSourceDirectory().setDirectory(s));
        }
        return out;
    }
}
