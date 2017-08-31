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
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.plugins.ide.eclipse.model.EclipseWtpComponent;
import org.gradle.plugins.ide.eclipse.model.FileReference;
import org.gradle.plugins.ide.eclipse.model.WbDependentModule;
import org.gradle.plugins.ide.eclipse.model.WbModuleEntry;
import org.gradle.plugins.ide.eclipse.model.WbResource;
import org.gradle.plugins.ide.eclipse.model.WtpComponent;
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor;
import org.gradle.plugins.ide.internal.resolver.model.IdeExtendedRepoFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeLocalFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class WtpComponentFactory {
    private final LocalComponentRegistry localComponentRegistry;

    public WtpComponentFactory(Project project) {
        localComponentRegistry = ((ProjectInternal) project).getServices().get(LocalComponentRegistry.class);
    }

    public void configure(final EclipseWtpComponent wtp, WtpComponent component) {
        List<WbModuleEntry> entries = Lists.newArrayList();
        entries.addAll(getEntriesFromSourceDirs(wtp));
        for (WbResource element : wtp.getResources()) {
            if (wtp.getProject().file(element.getSourcePath()).isDirectory()) {
                entries.add(element);
            }
        }
        entries.addAll(wtp.getProperties());
        Project project = wtp.getProject();
        entries.addAll(getEntriesFromConfigurations(project, configOrEmptySet(wtp.getRootConfigurations()), configOrEmptySet(wtp.getMinusConfigurations()), wtp, "/"));
        entries.addAll(getEntriesFromConfigurations(project, configOrEmptySet(wtp.getLibConfigurations()), configOrEmptySet(wtp.getMinusConfigurations()), wtp, wtp.getLibDeployPath()));
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
    private List<WbDependentModule> getEntriesFromConfigurations(Project project, Set<Configuration> plusConfigurations, Set<Configuration> minusConfigurations, EclipseWtpComponent wtp, String deployPath) {
        List<WbDependentModule> entries = Lists.newArrayList();
        entries.addAll(getEntriesFromProjectDependencies(project, plusConfigurations, minusConfigurations, deployPath));
        // All dependencies should be declared as Eclipse classpath entries by default. However if the project is not a Java
        // project, then as a fallback the dependencies are added to the component descriptor. This is useful for EAR
        // projects which typically are not Java projects.
        if (!project.getPlugins().hasPlugin(JavaPlugin.class)) {
            entries.addAll(getEntriesFromLibraries(plusConfigurations, minusConfigurations, wtp, deployPath));
        }
        return entries;
    }

    private List<WbDependentModule> getEntriesFromProjectDependencies(Project project, Set<Configuration> plusConfigurations, Set<Configuration> minusConfigurations, String deployPath) {
        IdeDependenciesExtractor extractor = new IdeDependenciesExtractor();
        Collection<IdeProjectDependency> dependencies = extractor.extractProjectDependencies(project, plusConfigurations, minusConfigurations);

        List<WbDependentModule> projectDependencies = Lists.newArrayList();
        for (IdeProjectDependency dependency : dependencies) {
            String moduleName = determineProjectName(dependency);
            projectDependencies.add(new WbDependentModule(deployPath, "module:/resource/" + moduleName + "/" + moduleName));
        }
        return projectDependencies;
    }

    private String determineProjectName(IdeProjectDependency dependency) {
        ComponentArtifactMetadata eclipseProjectArtifact = localComponentRegistry.findAdditionalArtifact(dependency.getProjectId(), "eclipse.project");
        return eclipseProjectArtifact == null ? dependency.getProjectName() : eclipseProjectArtifact.getName().getName();
    }

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


}
