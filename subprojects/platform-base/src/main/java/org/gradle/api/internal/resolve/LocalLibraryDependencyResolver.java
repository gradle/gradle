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
import org.gradle.api.Action;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.LibraryComponentIdentifier;
import org.gradle.api.artifacts.component.LibraryComponentSelector;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetaData;
import org.gradle.internal.component.model.*;
import org.gradle.internal.resolve.ArtifactResolveException;
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
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentSpecContainer;
import org.gradle.platform.base.LibrarySpec;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class LocalLibraryDependencyResolver implements DependencyToComponentIdResolver, ComponentMetaDataResolver, ArtifactResolver {
    private final ProjectLocator projectLocator;
    private final BinarySpecToArtifactConverterRegistry binarySpecToArtifactConverterRegistry;

    public LocalLibraryDependencyResolver(ProjectLocator projectLocator, BinarySpecToArtifactConverterRegistry registry) {
        this.projectLocator = projectLocator;
        this.binarySpecToArtifactConverterRegistry = registry;
    }

    @Override
    public void resolve(DependencyMetaData dependency, final BuildableComponentIdResolveResult result) {
        if (dependency.getSelector() instanceof LibraryComponentSelector) {
            LibraryComponentSelector selector = (LibraryComponentSelector) dependency.getSelector();
            final String selectorProjectPath = selector.getProjectPath();
            final String libraryName = selector.getLibraryName();
            final ProjectInternal project = projectLocator.locateProject(selectorProjectPath);
            List<String> candidateLibraries = new LinkedList<String>();
            if (project != null) {
                withLibrary(project, libraryName, candidateLibraries, new Action<LibrarySpec>() {
                    @Override
                    public void execute(LibrarySpec librarySpec) {
                        DefaultLibraryLocalComponentMetaData metaData = DefaultLibraryLocalComponentMetaData.newMetaData(selectorProjectPath, librarySpec.getName());
                        result.resolved(metaData.toResolveMetaData());
                    }
                });
            }
            if (!result.hasResult()) {
                String message = prettyErrorMessage(selector, project, selectorProjectPath, candidateLibraries);
                ModuleVersionResolveException failure = new ModuleVersionResolveException(selector, message);
                result.failed(failure);
            }
        }
    }

    private void withLibrary(ProjectInternal project,
                             String libraryName,
                             List<String> candidateLibraries,
                             Action<? super LibrarySpec> action) {
        ModelRegistry modelRegistry = project.getModelRegistry();
        ComponentSpecContainer components = modelRegistry.find(
            ModelPath.path("components"),
            ModelType.of(ComponentSpecContainer.class));
        if (components!=null) {
            ModelMap<? extends LibrarySpec> libraries = components.withType(LibrarySpec.class);
            boolean findSingleLibrary = "".equals(libraryName);
            Collection<? extends LibrarySpec> availableLibraries = libraries.values();
            if (candidateLibraries!=null) {
                for (LibrarySpec candidateLibrary : availableLibraries) {
                    candidateLibraries.add(String.format("'%s'", candidateLibrary.getName()));
                }
            }
            if (findSingleLibrary && availableLibraries.size() == 1) {
                libraryName = availableLibraries.iterator().next().getName();
            }
            if (!availableLibraries.isEmpty()) {
                // todo: this should probably be moved to a library selector
                // at some point to handle more complex library selection (flavors, ...)
                LibrarySpec library = libraries.get(libraryName);
                if (library != null) {
                    action.execute(library);
                }
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
        ComponentIdentifier componentId = component.getComponentId();
        if (isLibrary(componentId)) {
            doConvertArtifact(result, (LibraryComponentIdentifier) componentId);
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
            if (artifact instanceof PublishArtifactLocalArtifactMetaData) {
                result.resolved(((PublishArtifactLocalArtifactMetaData) artifact).getFile());
            } else {
                result.failed(new ArtifactResolveException("Unsupported artifact metadata type: "+artifact));
            }
        }
    }

    private void doConvertArtifact(final BuildableArtifactSetResolveResult result, LibraryComponentIdentifier componentId) {
        final LibraryComponentIdentifier libId = componentId;
        String projectPath = libId.getProjectPath();
        final String libraryName = libId.getLibraryName();
        ProjectInternal project = projectLocator.locateProject(projectPath);
        if (project!=null) {
            withLibrary(project, libraryName, null, new Action<LibrarySpec>() {
                @Override
                public void execute(LibrarySpec librarySpec) {
                    List<ComponentArtifactMetaData> artifacts = new LinkedList<ComponentArtifactMetaData>();
                    Collection<BinarySpec> binaries = librarySpec.getBinaries().values();
                    if (binaries.size()>1) {
                        result.failed(new ArtifactResolveException(String.format("Multiple binaries available for library '%s' : %s", libraryName, binaries)));
                    } else {
                        for (BinarySpec binary : binaries) {
                            BinarySpecToArtifactConverter<BinarySpec> factory = binarySpecToArtifactConverterRegistry.getConverter(binary);
                            if (factory != null) {
                                artifacts.add(factory.convertArtifact(libId, binary));
                            }
                        }
                        result.resolved(artifacts);
                    }
                }
            });
        }
        if (!result.hasResult()) {
            result.failed(new ArtifactResolveException("Unable to resolve artifact for " + componentId));
        }
    }

}
