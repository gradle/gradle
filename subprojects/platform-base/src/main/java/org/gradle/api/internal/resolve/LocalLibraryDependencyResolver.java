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
package org.gradle.api.internal.resolve;

import com.google.common.base.Joiner;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.LibraryComponentIdentifier;
import org.gradle.api.artifacts.component.LibraryComponentSelector;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetaData;
import org.gradle.internal.component.model.*;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetaData;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.ComponentSpecContainer;
import org.gradle.platform.base.LibrarySpec;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class LocalLibraryDependencyResolver implements DependencyToComponentIdResolver, ComponentMetaDataResolver, ArtifactResolver {
    private final ProjectFinder projectFinder;

    public LocalLibraryDependencyResolver(ProjectFinder projectFinder) {
        this.projectFinder = projectFinder;
    }

    @Override
    public void resolve(DependencyMetaData dependency, BuildableComponentIdResolveResult result) {
        if (dependency.getSelector() instanceof LibraryComponentSelector) {

            DefaultLocalComponentMetaData metaData = null;
            LibraryComponentSelector selector = (LibraryComponentSelector) dependency.getSelector();
            String selectorProjectPath = selector.getProjectPath();
            ProjectInternal project = projectFinder.getProject(selectorProjectPath);
            List<String> candidateLibraries = new LinkedList<String>();
            if (project != null) {
                ModelRegistry modelRegistry = project.getModelRegistry();
                ComponentSpecContainer components = modelRegistry.find(
                    ModelPath.path("components"),
                    ModelType.of(ComponentSpecContainer.class));
                if (components!=null) {
                    ModelMap<? extends LibrarySpec> libraries = components.withType(LibrarySpec.class);
                    String libraryName = selector.getLibraryName();
                    boolean findSingleLibrary = "".equals(libraryName);
                    Collection<? extends LibrarySpec> availableLibraries = libraries.values();
                    for (LibrarySpec candidateLibrary : availableLibraries) {
                        candidateLibraries.add(String.format("'%s'", candidateLibrary.getName()));
                    }
                    if (findSingleLibrary && availableLibraries.size() == 1) {
                        libraryName = availableLibraries.iterator().next().getName();
                    }
                    if (!availableLibraries.isEmpty()) {
                        // todo: this should probably be moved to a library selector
                        // at some point to handle more complex library selection (flavors, ...)
                        String version = project.getVersion().toString();
                        LibrarySpec library = libraries.get(libraryName);
                        if (library != null) {
                            metaData = DefaultLibraryLocalComponentMetaData.newMetaData(selectorProjectPath, libraryName, version);
                        }
                    }
                }
            }
            if (metaData != null) {
                result.resolved(metaData.toResolveMetaData());
            } else {
                String message = prettyErrorMessage(selector, project, selectorProjectPath, candidateLibraries);
                ModuleVersionResolveException failure = new ModuleVersionResolveException(selector, message);
                result.failed(failure);
            }
        }
    }

    private static String prettyErrorMessage(
        LibraryComponentSelector selector,
        ProjectInternal project,
        String selectorProjectPath,
        List<String> candidateLibraries) {
        StringBuilder sb = new StringBuilder("Could not resolve dependency '");
        sb.append(selector);
        sb.append("'");
        if ("".equals(selector.getLibraryName()) || candidateLibraries.isEmpty()) {
            sb.append(". Project '").append(selectorProjectPath).append("'");
            if (project==null) {
                sb.append(" not found.");
            } else if (candidateLibraries.isEmpty()) {
                sb.append(" doesn't define any library.");
            } else {
                sb.append(" contains more than one library. Please select one of ");
                Joiner.on(", ").appendTo(sb, candidateLibraries);
            }
        } else {
            sb.append(" on project '").append(selectorProjectPath).append("'");
            sb.append(". Did you want to use ");
            if (candidateLibraries.size()==1) {
                sb.append(candidateLibraries.get(0));
            } else {
                sb.append("one of ");
                Joiner.on(", ").appendTo(sb, candidateLibraries);
            }
            sb.append("?");
        }
        return sb.toString();
    }

    @Override
    public void resolve(ComponentIdentifier identifier, ComponentOverrideMetadata componentOverrideMetadata, BuildableComponentResolveResult result) {
        if (isLibrary(identifier)) {
            throw new RuntimeException("Not yet implemented");
        }
    }

    private boolean isLibrary(ComponentIdentifier identifier) {
        return identifier instanceof LibraryComponentIdentifier;
    }

    @Override
    public void resolveModuleArtifacts(ComponentResolveMetaData component, ComponentUsage usage, BuildableArtifactSetResolveResult result) {
        if (isLibrary(component.getComponentId())) {
            result.resolved(Collections.<ComponentArtifactMetaData>emptyList());
        }
    }

    @Override
    public void resolveModuleArtifacts(ComponentResolveMetaData component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        if (isLibrary(component.getComponentId())) {
            result.resolved(Collections.<ComponentArtifactMetaData>emptyList());
        }
    }

    @Override
    public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
        if (isLibrary(artifact.getComponentId())) {
            result.resolved(new File("<no file>"));
        }
    }
}
