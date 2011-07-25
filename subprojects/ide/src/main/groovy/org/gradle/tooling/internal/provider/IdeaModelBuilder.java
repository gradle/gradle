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
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.plugins.ide.idea.model.IdeaProject;
import org.gradle.tooling.internal.idea.DefaultIdeaModule;
import org.gradle.tooling.internal.idea.DefaultIdeaProject;
import org.gradle.tooling.internal.protocol.InternalIdeaProject;
import org.gradle.tooling.internal.protocol.ProjectVersion3;

import java.util.Collection;
import java.util.LinkedHashSet;
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

        Set<DefaultIdeaModule> modules = new LinkedHashSet<DefaultIdeaModule>();
        for (IdeaModule module: projectModel.getModules()) {
            buildModule(modules, module, out);
        }
        out.setChildren(modules);

        return out;
    }

    private void buildModule(Collection<DefaultIdeaModule> modules, IdeaModule module, DefaultIdeaProject ideaProject) {
        DefaultIdeaModule defaultIdeaModule = new DefaultIdeaModule()
                .setName(module.getName())
                .setContentRoots(asList(module.getContentRoot()))
                .setParent(ideaProject)
                .setInheritOutputDirs(module.getInheritOutputDirs() != null ? module.getInheritOutputDirs() : false)
                .setOutputDir(module.getOutputDir())
                .setTestOutputDir(module.getTestOutputDir());

//        defaultIdeaModule.setSourceDirectories(new LinkedList<File>(module.getSourceDirs()));
//        defaultIdeaModule.setTestDirectories(new LinkedList<File>(module.getTestSourceDirs()));
//        defaultIdeaModule.setExcludeDirectories(new LinkedList<File>(module.getExcludeDirs()));
//        defaultIdeaModule.setModuleFileDir(module.getIml().getGenerateTo());
//
//        Set<Dependency> resolved = module.resolveDependencies();
//        for (Dependency dependency: resolved) {
//            if (dependency instanceof ModuleLibrary) {
//                List<IdeaLibraryDependency> dependencies = new LinkedList<IdeaLibraryDependency>();
//                File file = ((ModuleLibrary) dependency).getSingleJar();
//                File javadoc = ((ModuleLibrary) dependency).getSingleJavadoc();
//                File source = ((ModuleLibrary) dependency).getSingleSource();
//                boolean exported = ((ModuleLibrary) dependency).getExported();
//                String scope = ((ModuleLibrary) dependency).getScope();
//                dependencies.add(new DefaultIdeaLibraryDependency(file, source, javadoc, scope, exported));
//                defaultIdeaModule.setLibraryDependencies(dependencies);
//            } else if (dependency instanceof ModuleDependency) {
//                List<IdeaModuleDependency> dependencies = new LinkedList<IdeaModuleDependency>();
//                String name = ((ModuleDependency) dependency).getName();
//                boolean exported = ((ModuleDependency) dependency).getExported();
//                String scope = ((ModuleDependency) dependency).getScope();
//                dependencies.add(new DefaultIdeaModuleDependency(scope, name, exported));
//                defaultIdeaModule.setModuleDependencies(dependencies);
//            }
//        }

        modules.add(defaultIdeaModule);
    }
}