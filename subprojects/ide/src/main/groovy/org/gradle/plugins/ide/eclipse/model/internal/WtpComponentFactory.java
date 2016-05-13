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

package org.gradle.plugins.ide.eclipse.model.internal;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Specs;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.EclipseWtpComponent;
import org.gradle.plugins.ide.eclipse.model.FileReference;
import org.gradle.plugins.ide.eclipse.model.WbDependentModule;
import org.gradle.plugins.ide.eclipse.model.WbModuleEntry;
import org.gradle.plugins.ide.eclipse.model.WbResource;
import org.gradle.plugins.ide.eclipse.model.WtpComponent;
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor;
import org.gradle.plugins.ide.internal.resolver.model.IdeExtendedRepoFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeLocalFileDependency;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class WtpComponentFactory {
    public void configure(final EclipseWtpComponent wtp, WtpComponent component) {
        List<WbModuleEntry> entries = Lists.newArrayList();
        entries.addAll(getEntriesFromSourceDirs(wtp));
        for (WbResource element : wtp.getResources()) {
            if (wtp.getProject().file(element.getSourcePath()).isDirectory()) {
                entries.add(element);
            }
        }
        entries.addAll(wtp.getProperties());
        // for ear files root deps are NOT transitive; wars don't use root deps so this doesn't hurt them
        // TODO: maybe do this in a more explicit way, via config or something
        entries.addAll(getEntriesFromConfigurations(configOrEmptySet(wtp.getRootConfigurations()), configOrEmptySet(wtp.getMinusConfigurations()), wtp, "/", false));
        entries.addAll(getEntriesFromConfigurations(configOrEmptySet(wtp.getLibConfigurations()), configOrEmptySet(wtp.getMinusConfigurations()), wtp, wtp.getLibDeployPath(), true));
        component.configure(wtp.getDeployName(), wtp.getContextPath(), entries);
    }

    private Set<Configuration> configOrEmptySet(Set<Configuration> configuration) {
        if (configuration == null) {
            return Sets.newHashSet();
        } else {
            return configuration;
        }
    }

    private List<WbResource> getEntriesFromSourceDirs(EclipseWtpComponent wtp) {
        List<WbResource> result = Lists.newArrayList();
        if (wtp.getSourceDirs() != null) {
            for (File dir : wtp.getSourceDirs()) {
                if (dir.isDirectory()) {
                    result.add(new WbResource(wtp.getClassesDeployPath(), wtp.getProject().relativePath(dir)));
                }
            }
        }
        return result;
    }

    private List<WbDependentModule> getEntriesFromConfigurations(Set<Configuration> plusConfigurations, Set<Configuration> minusConfigurations, EclipseWtpComponent wtp, String deployPath, boolean transitive) {
        List<WbDependentModule> entries = Lists.newArrayList();
        entries.addAll(getEntriesFromProjectDependencies(plusConfigurations, minusConfigurations, deployPath, transitive));
        entries.addAll(getEntriesFromLibraries(plusConfigurations, minusConfigurations, wtp, deployPath));
        return entries;
    }

    // must include transitive project dependencies
    private List<WbDependentModule> getEntriesFromProjectDependencies(Set<Configuration> plusConfigurations, Set<Configuration> minusConfigurations, String deployPath, boolean transitive) {
        Set<Dependency> dependencies = getDependencies(plusConfigurations, minusConfigurations, Specs.<Dependency>isInstance(ProjectDependency.class));

        List<Project> projects = Lists.newArrayList();
        for (Dependency dependency : dependencies) {
            projects.add(((ProjectDependency)dependency).getDependencyProject());
        }

        Set<Project> allProjects = Sets.newLinkedHashSet();
        allProjects.addAll(projects);
        if (transitive) {
            for (Project project : projects) {
                collectDependedUponProjects(project, allProjects);
            }
        }

        List<WbDependentModule> projectDependencies = Lists.newArrayList();
        for (Project project : allProjects) {
            String moduleName;
            if (project.getPlugins().hasPlugin(EclipsePlugin.class)) {
                moduleName = project.getExtensions().getByType(EclipseModel.class).getProject().getName();
            } else {
                moduleName = project.getName();
            }
            projectDependencies.add(new WbDependentModule(deployPath, "module:/resource/" + moduleName + "/" + moduleName));
        }
        return projectDependencies;
    }

    // TODO: might have to search all class paths of all source sets for project dependencies, not just runtime configuration
    private void collectDependedUponProjects(org.gradle.api.Project project, Set<org.gradle.api.Project> result) {
        Configuration runtimeConfig = project.getConfigurations().findByName("runtime");
        if (runtimeConfig != null) {
            DomainObjectSet<ProjectDependency> projectDeps = runtimeConfig.getAllDependencies().withType(ProjectDependency.class);

            List<Project> dependedUponProjects = Lists.newArrayList();
            for (ProjectDependency projectDep : projectDeps) {
                dependedUponProjects.add(projectDep.getDependencyProject());
            }

            result.addAll(dependedUponProjects);
            for (Project dependedUponProject : dependedUponProjects) {
                collectDependedUponProjects(dependedUponProject, result);
            }
        }
    }

    // must NOT include transitive library dependencies
    private List<WbDependentModule> getEntriesFromLibraries(Set<Configuration> plusConfigurations, Set<Configuration> minusConfigurations, EclipseWtpComponent wtp, String deployPath) {
        IdeDependenciesExtractor extractor = new IdeDependenciesExtractor();
        //below is not perfect because we're skipping the unresolved dependencies completely
        //however, it should be better anyway. Sometime soon we will hopefully change the wtp component stuff
        Collection<IdeExtendedRepoFileDependency> externals = extractor.resolvedExternalDependencies(plusConfigurations, minusConfigurations);
        Collection<IdeLocalFileDependency> locals = extractor.extractLocalFileDependencies(plusConfigurations, minusConfigurations);

        Collection<File> libFiles = Lists.newArrayList();
        for (IdeExtendedRepoFileDependency dependency : externals) {
            libFiles.add(dependency.getFile());
        }

        for (IdeLocalFileDependency dependency :locals) {
            libFiles.add(dependency.getFile());
        }

        List<WbDependentModule> libraryEntries = Lists.newArrayList();
        for(File file : libFiles) {
            libraryEntries.add(createWbDependentModuleEntry(file, wtp.getFileReferenceFactory(), deployPath));
        }
        return libraryEntries;
    }

    private WbDependentModule createWbDependentModuleEntry(File file, FileReferenceFactory fileReferenceFactory, String deployPath) {
        FileReference ref = fileReferenceFactory.fromFile(file);
        String handleSnippet = ref.isRelativeToPathVariable() ? "var/" + ref.getPath() : "lib/" + ref.getPath();
        return new WbDependentModule(deployPath, "module:/classpath/" + handleSnippet);
    }

    private Set<Dependency> getDependencies(Set<Configuration> plusConfigurations, Set<Configuration> minusConfigurations, Spec<Dependency> filter) {
        Set<Dependency> declaredDependencies = Sets.newLinkedHashSet();
        for (Configuration configuration : plusConfigurations) {
            declaredDependencies.addAll(configuration.getAllDependencies().matching(filter));
        }
        for (Configuration configuration : minusConfigurations) {
            declaredDependencies.removeAll(configuration.getAllDependencies().matching(filter));
        }
        return declaredDependencies;
    }
}
