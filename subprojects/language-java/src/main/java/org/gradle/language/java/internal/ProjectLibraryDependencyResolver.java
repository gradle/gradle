/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.language.java.internal;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.LibraryComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.LocalComponentFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.component.local.model.DefaultLibraryComponentIdentifier;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetaData;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.ComponentSpecContainer;
import org.gradle.platform.base.LibrarySpec;

import java.util.Collections;

// TODO: this should probably be in the platform-base module somehow, but it doesn't depend on dependency-resolution
// module so it's not possible yet
public class ProjectLibraryDependencyResolver extends ProjectDependencyResolver {
    private final ProjectInternal defaultProject;

    public ProjectLibraryDependencyResolver(ProjectInternal project, ProjectComponentRegistry projectComponentRegistry, LocalComponentFactory localComponentFactory, DependencyToComponentIdResolver delegateIdResolver, ComponentMetaDataResolver delegateComponentResolver) {
        super(projectComponentRegistry, localComponentFactory, delegateIdResolver, delegateComponentResolver);
        defaultProject = project;
    }

    @Override
    public void resolve(DependencyMetaData dependency, BuildableComponentIdResolveResult result) {
        if (dependency.getSelector() instanceof LibraryComponentSelector) {
            DefaultLocalComponentMetaData metaData = null;
            LibraryComponentSelector selector = (LibraryComponentSelector) dependency.getSelector();
            ProjectInternal project = defaultProject;
            if (selector.getProjectPath()!=null) {
                project = project.getRootProject().findProject(selector.getProjectPath());
            }
            if (project!=null) {
                ComponentSpecContainer components = project.getModelRegistry().realize(
                    ModelPath.path("components"),
                    ModelType.of(ComponentSpecContainer.class));
                ModelMap<? extends LibrarySpec> libraries = components.withType(LibrarySpec.class);
                String libraryName = selector.getLibraryName();
                if (libraryName==null && libraries.size()==1) {
                    libraryName = libraries.values().iterator().next().getName();
                }
                if (libraryName!=null) {
                    String version = project.getVersion().toString();
                    String projectPath = project.getPath();
                    LibrarySpec library = libraries.get(libraryName);
                    if (library != null) {
                        ModuleVersionIdentifier id = new DefaultModuleVersionIdentifier(
                            projectPath, libraryName, version
                        );
                        ComponentIdentifier component = new DefaultLibraryComponentIdentifier(projectPath, library.getName());
                        metaData = new DefaultLocalComponentMetaData(id, component, Project.DEFAULT_STATUS);
                        metaData.addConfiguration(DefaultLibraryComponentIdentifier.libraryToConfigurationName(projectPath, libraryName), "Configuration for "+libraryName, Collections.<String>emptySet(), Collections.singleton(DefaultLibraryComponentIdentifier.libraryToConfigurationName(projectPath, libraryName)), true, true);
                    }
                }
            }
            if (metaData!=null) {
                result.resolved(metaData.toResolveMetaData());
            } else {
                result.failed(new ModuleVersionResolveException(selector, String.format("Cannot resolve dependency %s", selector)));
            }
        } else {
            super.resolve(dependency, result);
        }
    }

}
