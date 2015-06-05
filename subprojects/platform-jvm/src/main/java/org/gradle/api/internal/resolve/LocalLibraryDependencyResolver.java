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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.LibraryComponentIdentifier;
import org.gradle.api.artifacts.component.LibraryComponentSelector;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
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
import org.gradle.jvm.JvmLibrarySpec;
import org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetaData;
import org.gradle.language.base.internal.resolve.LibraryResolveException;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentSpecContainer;
import org.gradle.platform.base.LibrarySpec;

import java.util.*;

// TODO: This should really live in platform-base, however we need to inject the library requirements at some
// point, and for now it is hardcoded to JVM libraries
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
            LibraryResolutionResult resolutionResult = doResolve(project, libraryName);
            LibrarySpec selectedLibrary = resolutionResult.getSelectedLibrary();
            if (selectedLibrary != null) {
                DefaultTaskDependency buildDependencies = new DefaultTaskDependency();
                for (BinarySpec spec : selectedLibrary.getBinaries().values()) {
                    buildDependencies.add(spec.getBuildTask());
                }
                DefaultLibraryLocalComponentMetaData metaData = DefaultLibraryLocalComponentMetaData.newMetaData(selectorProjectPath, selectedLibrary.getName(), buildDependencies);
                result.resolved(metaData.toResolveMetaData());
            } else {
                String message = prettyErrorMessage(selector, resolutionResult);
                ModuleVersionResolveException failure = new ModuleVersionResolveException(selector, new LibraryResolveException(message));
                result.failed(failure);
            }
        }
    }

    private LibraryResolutionResult doResolve(ProjectInternal project,
                                              String libraryName) {
        if (project != null) {
            ModelRegistry modelRegistry = project.getModelRegistry();
            ComponentSpecContainer components = modelRegistry.find(
                ModelPath.path("components"),
                ModelType.of(ComponentSpecContainer.class));
            if (components != null) {
                ModelMap<? extends LibrarySpec> libraries = components.withType(LibrarySpec.class);
                return LibraryResolutionResult.from(libraries.values(), libraryName);
            } else {
                return LibraryResolutionResult.empty();
            }
        } else {
            return LibraryResolutionResult.projectNotFound();
        }
    }

    private static String prettyErrorMessage(
        LibraryComponentSelector selector,
        LibraryResolutionResult result) {
        boolean hasLibraries = result.hasLibraries();
        List<String> candidateLibraries = formatLibraryNames(result.getCandidateLibraries());
        String projectPath = selector.getProjectPath();
        String libraryName = selector.getLibraryName();

        StringBuilder sb = new StringBuilder("Project '").append(projectPath).append("'");
        if (Strings.isNullOrEmpty(libraryName) || !hasLibraries) {
            if (result.isProjectNotFound()) {
                sb.append(" not found.");
            } else if (!hasLibraries) {
                sb.append(" doesn't define any library.");
            } else {
                sb.append(" contains more than one library. Please select one of ");
                Joiner.on(", ").appendTo(sb, candidateLibraries);
            }
        } else {
            LibrarySpec notMatchingRequirements = result.getNonMatchingLibrary();
            if (notMatchingRequirements!=null) {
                sb.append(" contains a library named '").append(libraryName)
                  .append("' but it is not a ")
                  .append(JvmLibrarySpec.class.getSimpleName());
            } else {
                sb.append(" does not contain library '").append(libraryName).append("'. Did you want to use ");
                if (candidateLibraries.size() == 1) {
                    sb.append(candidateLibraries.get(0));
                } else {
                    sb.append("one of ");
                    Joiner.on(", ").appendTo(sb, candidateLibraries);
                }
                sb.append("?");
            }
        }
        return sb.toString();
    }

    private static List<String> formatLibraryNames(List<String> libs) {
        List<String> list = Lists.transform(libs, new Function<String, String>() {
            @Override
            public String apply(String input) {
                return String.format("'%s'", input);
            }
        });
        return Ordering.natural().sortedCopy(list);
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
                result.failed(new ArtifactResolveException("Unsupported artifact metadata type: " + artifact));
            }
        }
    }

    private void doConvertArtifact(final BuildableArtifactSetResolveResult result, LibraryComponentIdentifier componentId) {
        String projectPath = componentId.getProjectPath();
        final String libraryName = componentId.getLibraryName();
        ProjectInternal project = projectLocator.locateProject(projectPath);
        LibraryResolutionResult resolutionResult = doResolve(project, libraryName);
        LibrarySpec selectedLibrary = resolutionResult.getSelectedLibrary();
        if (selectedLibrary != null) {
            List<ComponentArtifactMetaData> artifacts = new LinkedList<ComponentArtifactMetaData>();
            Collection<BinarySpec> binaries = selectedLibrary.getBinaries().values();
            if (binaries.size() > 1) {
                result.failed(new ArtifactResolveException(String.format("Multiple binaries available for library '%s' : %s", libraryName, binaries)));
            } else {
                for (BinarySpec binary : binaries) {
                    BinarySpecToArtifactConverter<BinarySpec> factory = binarySpecToArtifactConverterRegistry.getConverter(binary);
                    if (factory != null) {
                        artifacts.add(factory.convertArtifact(componentId, binary));
                    }
                }
                result.resolved(artifacts);
            }
        }
        if (!result.hasResult()) {
            result.failed(new ArtifactResolveException("Unable to resolve artifact for " + componentId));
        }
    }

    /**
     * Intermediate data structure used to store the result of a resolution and help at building
     * an understandable error message in case resolution fails.
     */
    private static class LibraryResolutionResult {
        private static final LibraryResolutionResult EMPTY = new LibraryResolutionResult();
        private static final LibraryResolutionResult PROJECT_NOT_FOUND = new LibraryResolutionResult();
        private final Map<String, LibrarySpec> libsMatchingRequirements;
        private final Map<String, LibrarySpec> libsNotMatchingRequirements;

        private LibrarySpec selectedLibrary;
        private LibrarySpec nonMatchingLibrary;

        public static LibraryResolutionResult from(Collection<? extends LibrarySpec> libraries, String libraryName) {
            LibraryResolutionResult result = new LibraryResolutionResult();
            for (LibrarySpec librarySpec : libraries) {
                if (result.acceptLibrary(librarySpec)) {
                    result.libsMatchingRequirements.put(librarySpec.getName(), librarySpec);
                } else {
                    result.libsNotMatchingRequirements.put(librarySpec.getName(), librarySpec);
                }
            }
            result.resolve(libraryName);
            return result;
        }

        public static LibraryResolutionResult projectNotFound() {
            return PROJECT_NOT_FOUND;
        }

        public static LibraryResolutionResult empty() {
            return EMPTY;
        }

        private LibraryResolutionResult() {
            this.libsMatchingRequirements = Maps.newHashMap();
            this.libsNotMatchingRequirements = Maps.newHashMap();
        }

        private boolean acceptLibrary(LibrarySpec librarySpec) {
            // TODO: this should be parametrized, and provided in some way to the resolver
            // once this is done, can move to platform-base
            return librarySpec instanceof JvmLibrarySpec;
        }

        private LibrarySpec getSingleMatchingLibrary() {
            if (libsMatchingRequirements.size() == 1) {
                return libsMatchingRequirements.values().iterator().next();
            }
            return null;
        }

        private void resolve(String libraryName) {
            boolean findSingleLibrary = "".equals(libraryName);
            if (findSingleLibrary && getSingleMatchingLibrary() != null) {
                libraryName = getSingleMatchingLibrary().getName();
            }

            selectedLibrary = libsMatchingRequirements.get(libraryName);
            nonMatchingLibrary = libsNotMatchingRequirements.get(libraryName);
        }

        public boolean isProjectNotFound() {
            return PROJECT_NOT_FOUND==this;
        }

        public boolean hasLibraries() {
            return !libsMatchingRequirements.isEmpty() || !libsNotMatchingRequirements.isEmpty();
        }

        public LibrarySpec getSelectedLibrary() {
            return selectedLibrary;
        }

        public LibrarySpec getNonMatchingLibrary() {
            return nonMatchingLibrary;
        }

        public List<String> getCandidateLibraries() {
            return Lists.newArrayList(libsMatchingRequirements.keySet());
        }
    }

}
