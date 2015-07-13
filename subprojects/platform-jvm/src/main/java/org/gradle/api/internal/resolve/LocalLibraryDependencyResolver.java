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
import com.google.common.collect.*;
import org.gradle.api.UnknownProjectException;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.artifacts.component.LibraryComponentSelector;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier;
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
import org.gradle.jvm.JarBinarySpec;
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.jvm.JvmLibrarySpec;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetaData;
import org.gradle.language.base.internal.model.DefaultVariantsMetaData;
import org.gradle.language.base.internal.model.VariantsMetaData;
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
    private static final Comparator<JavaPlatform> JAVA_PLATFORM_COMPARATOR = new Comparator<JavaPlatform>() {
        @Override
        public int compare(JavaPlatform o1, JavaPlatform o2) {
            return o1.getTargetCompatibility().compareTo(o2.getTargetCompatibility());
        }
    };
    private static final Comparator<JvmBinarySpec> JVM_BINARY_SPEC_COMPARATOR = new Comparator<JvmBinarySpec>() {
        @Override
        public int compare(JvmBinarySpec o1, JvmBinarySpec o2) {
            return o1.getDisplayName().compareTo(o2.getDisplayName());
        }
    };
    private static final String TARGET_PLATFORM = "targetPlatform";

    private final ProjectModelResolver projectModelResolver;
    private final VariantsMetaData variantsMetaData;
    private final JavaPlatform javaPlatform;
    private final Set<String> resolveDimensions;

    public LocalLibraryDependencyResolver(ProjectModelResolver projectModelResolver, VariantsMetaData variantsMetaData) {
        this.projectModelResolver = projectModelResolver;
        this.variantsMetaData = variantsMetaData;
        this.javaPlatform = variantsMetaData.getValueAsType(JavaPlatform.class, TARGET_PLATFORM);
        this.resolveDimensions = variantsMetaData.getNonNullDimensions();
    }

    @Override
    public void resolve(DependencyMetaData dependency, final BuildableComponentIdResolveResult result) {
        if (dependency.getSelector() instanceof LibraryComponentSelector) {
            LibraryComponentSelector selector = (LibraryComponentSelector) dependency.getSelector();
            final String selectorProjectPath = selector.getProjectPath();
            final String libraryName = selector.getLibraryName();
            LibraryResolutionResult resolutionResult = doResolve(selectorProjectPath, libraryName);
            LibrarySpec selectedLibrary = resolutionResult.getSelectedLibrary();
            if (selectedLibrary != null) {
                Collection<BinarySpec> allBinaries = selectedLibrary.getBinaries().values();
                Collection<? extends BinarySpec> compatibleBinaries = filterBinaries(allBinaries);
                if (!allBinaries.isEmpty() && compatibleBinaries.isEmpty()) {
                    // no compatible variant found
                    result.failed(new ModuleVersionResolveException(selector, noCompatiblePlatformErrorMessage(libraryName, allBinaries)));
                } else if (compatibleBinaries.size() > 1) {
                    result.failed(new ModuleVersionResolveException(selector, multipleBinariesForSameVariantErrorMessage(libraryName, compatibleBinaries)));
                } else {
                    JarBinarySpec jarBinary = (JarBinarySpec) compatibleBinaries.iterator().next();
                    DefaultTaskDependency buildDependencies = new DefaultTaskDependency();
                    buildDependencies.add(jarBinary);

                    DefaultLibraryLocalComponentMetaData metaData = DefaultLibraryLocalComponentMetaData.newMetaData(jarBinary.getId(), buildDependencies);
                    metaData.addArtifacts(DefaultLibraryBinaryIdentifier.CONFIGURATION_NAME, Collections.singleton(createJarPublishArtifact(jarBinary)));

                    result.resolved(metaData);
                }
            }
            if (!result.hasResult()) {
                String message = prettyResolutionErrorMessage(selector, resolutionResult);
                ModuleVersionResolveException failure = new ModuleVersionResolveException(selector, new LibraryResolveException(message));
                result.failed(failure);
            }
        }
    }

    private PublishArtifact createJarPublishArtifact(JarBinarySpec jarBinarySpec) {
        return new LibraryPublishArtifact("jar", jarBinarySpec.getJarFile());
    }

    private Collection<? extends BinarySpec> filterBinaries(Collection<BinarySpec> values) {
        if (values.isEmpty()) {
            return values;
        }
        TreeMultimap<JavaPlatform, JvmBinarySpec> platformToBinary = TreeMultimap.create(JAVA_PLATFORM_COMPARATOR, JVM_BINARY_SPEC_COMPARATOR);
        for (BinarySpec binarySpec : values) {
            if (binarySpec instanceof JarBinarySpec) {
                JarBinarySpec jvmSpec = (JarBinarySpec) binarySpec;
                if (jvmSpec.getTargetPlatform().getTargetCompatibility().compareTo(javaPlatform.getTargetCompatibility()) <= 0) {
                    VariantsMetaData binaryVariants = DefaultVariantsMetaData.extractFrom(jvmSpec);
                    Set<String> commonsDimensions = Sets.intersection(resolveDimensions, binaryVariants.getNonNullDimensions());
                    boolean matching = incompatibleDimensionTypes(commonsDimensions, binaryVariants).isEmpty();
                    if (matching) {
                        for (String dimension : commonsDimensions) {
                            if (!isPlatformDimension(dimension)) {
                                String resolveValue = variantsMetaData.getValueAsString(dimension);
                                String binaryValue = binaryVariants.getValueAsString(dimension);
                                matching = com.google.common.base.Objects.equal(resolveValue, binaryValue);
                                if (!matching) {
                                    break;
                                }
                            }
                        }
                    }
                    if (matching) {
                        platformToBinary.put(jvmSpec.getTargetPlatform(), jvmSpec);
                    }
                }
            }
        }
        if (platformToBinary.isEmpty()) {
            return Collections.emptyList();
        }
        JavaPlatform first = platformToBinary.keySet().last();
        return platformToBinary.get(first);
    }

    private Set<String> incompatibleDimensionTypes(Set<String> testedDimensions, VariantsMetaData binaryVariant) {
        Set<String> result = Sets.newHashSet();
        for (String commonDimension : testedDimensions) {
            Class<?> resolveType = variantsMetaData.getDimensionType(commonDimension);
            Class<?> binaryVariantType = binaryVariant.getDimensionType(commonDimension);
            if (binaryVariantType != null && !resolveType.isAssignableFrom(binaryVariantType)) {
                result.add(commonDimension);
            }
        }
        return result;
    }

    private LibraryResolutionResult doResolve(String projectPath,
                                              String libraryName) {
        try {
            ModelRegistry projectModel = projectModelResolver.resolveProjectModel(projectPath);
            ComponentSpecContainer components = projectModel.find(
                ModelPath.path("components"),
                ModelType.of(ComponentSpecContainer.class));
            if (components != null) {
                ModelMap<? extends LibrarySpec> libraries = components.withType(LibrarySpec.class);
                return LibraryResolutionResult.from(libraries.values(), libraryName);
            } else {
                return LibraryResolutionResult.empty();
            }
        } catch (UnknownProjectException e) {
            return LibraryResolutionResult.projectNotFound();
        }
    }

    private String multipleBinariesForSameVariantErrorMessage(String libraryName, Collection<? extends BinarySpec> binaries) {
        List<String> binaryDescriptors = new ArrayList<String>(binaries.size());
        StringBuilder binaryDescriptor = new StringBuilder();
        for (BinarySpec variant : binaries) {
            binaryDescriptor.setLength(0);
            binaryDescriptor.append("   - ").append(variant.getDisplayName()).append(":\n");
            VariantsMetaData metaData = DefaultVariantsMetaData.extractFrom(variant);
            Set<String> dimensions = new TreeSet<String>(metaData.getNonNullDimensions());
            if (dimensions.size() > 1) { // 1 because of targetPlatform
                for (String dimension : dimensions) {
                    binaryDescriptor.append("       * ").append(dimension).append(" '").append(metaData.getValueAsString(dimension)).append("'\n");

                }
                binaryDescriptors.add(binaryDescriptor.toString());
            }
        }
        if (binaryDescriptors.isEmpty()) {
            return String.format("Multiple binaries available for library '%s' (%s) : %s", libraryName, javaPlatform, binaries);
        } else {
            // custom variants on binaries
            StringBuilder sb = new StringBuilder(String.format("Multiple binaries available for library '%s' (%s) :\n", libraryName, javaPlatform));
            for (String descriptor : binaryDescriptors) {
                sb.append(descriptor);
            }
            return sb.toString();
        }
    }

    private static boolean isPlatformDimension(String dimension) {
        return TARGET_PLATFORM.equals(dimension);
    }

    private String noCompatiblePlatformErrorMessage(String libraryName, Collection<BinarySpec> allBinaries) {
        if (resolveDimensions.size() == 1) { // 1 because of targetPlatform
            List<String> availablePlatforms = Lists.transform(
                Lists.newArrayList(allBinaries), new Function<BinarySpec, String>() {
                    @Override
                    public String apply(BinarySpec input) {
                        return input instanceof JarBinarySpec ? ((JarBinarySpec) input).getTargetPlatform().toString() : input.toString();
                    }
                }
            );
            return String.format("Cannot find a compatible binary for library '%s' (%s). Available platforms: %s", libraryName, javaPlatform, availablePlatforms);
        } else {
            final boolean moreThanOneBinary = allBinaries.size() > 1;
            Set<String> availablePlatforms = new TreeSet<String>(Lists.transform(
                Lists.newArrayList(allBinaries), new Function<BinarySpec, String>() {
                    @Override
                    public String apply(BinarySpec input) {
                        if (input instanceof JarBinarySpec) {
                            JavaPlatform targetPlatform = ((JarBinarySpec) input).getTargetPlatform();
                            if (moreThanOneBinary) {
                                return String.format("'%s' on %s", targetPlatform.getName(), input.getDisplayName());
                            }
                            return String.format("'%s'", targetPlatform.getName(), input.getDisplayName());
                        }
                        return null;
                    }
                }
            ));
            StringBuilder error = new StringBuilder(String.format("Cannot find a compatible binary for library '%s' (%s).\n", libraryName, javaPlatform));
            Joiner joiner = Joiner.on(",").skipNulls();
            error.append("    Required platform '").append(javaPlatform.getName()).append("', ");
            error.append("available: ").append(joiner.join(availablePlatforms));
            error.append("\n");
            HashMultimap<String, String> variants = HashMultimap.create();
            for (BinarySpec spec : allBinaries) {
                VariantsMetaData md = DefaultVariantsMetaData.extractFrom(spec);
                Set<String> incompatibleDimensionTypes = incompatibleDimensionTypes(resolveDimensions, md);
                for (String dimension : resolveDimensions) {
                    String value = md.getValueAsString(dimension);
                    if (value != null) {
                        String message;
                        if (moreThanOneBinary) {
                            message = String.format("'%s' on %s", value, spec.getDisplayName());
                        } else {
                            message = String.format("'%s'", value);
                        }
                        if (incompatibleDimensionTypes.contains(dimension)) {
                            message = String.format("%s but with an incompatible type (expected '%s' was '%s')", message, variantsMetaData.getDimensionType(dimension).getName(), md.getDimensionType(dimension).getName());
                        }
                        variants.put(dimension, message);
                    }
                }
            }

            for (String dimension : resolveDimensions) {
                if (!isPlatformDimension(dimension)) {
                    error.append("    Required ").append(dimension).append(" '").append(variantsMetaData.getValueAsString(dimension)).append("'");
                    Set<String> available = new TreeSet<String>(variants.get(dimension));
                    if (!available.isEmpty()) {
                        error.append(", available: ").append(joiner.join(available)).append("\n");
                    } else {
                        error.append(" but no compatible binary was found\n");
                    }
                }
            }
            return error.toString();
        }
    }

    private static String prettyResolutionErrorMessage(
        LibraryComponentSelector selector,
        LibraryResolutionResult result) {
        boolean hasLibraries = result.hasLibraries();
        List<String> candidateLibraries = formatLibraryNames(result.getCandidateLibraries());
        String projectPath = selector.getProjectPath();
        String libraryName = selector.getLibraryName();

        StringBuilder sb = new StringBuilder("Project '").append(projectPath).append("'");
        if (libraryName == null || !hasLibraries) {
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
            if (notMatchingRequirements != null) {
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
        return identifier instanceof LibraryBinaryIdentifier;
    }

    @Override
    public void resolveModuleArtifacts(ComponentResolveMetaData component, ComponentUsage usage, BuildableArtifactSetResolveResult result) {
        ComponentIdentifier componentId = component.getComponentId();
        if (isLibrary(componentId)) {
            ConfigurationMetaData configuration = component.getConfiguration(usage.getConfigurationName());
            if (configuration != null) {
                Set<ComponentArtifactMetaData> artifacts = configuration.getArtifacts();
                result.resolved(artifacts);
            }
            if (!result.hasResult()) {
                result.failed(new ArtifactResolveException(String.format("Unable to resolve artifact for %s", componentId)));
            }
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

    /**
     * Intermediate data structure used to store the result of a resolution and help at building an understandable error message in case resolution fails.
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
            return !librarySpec.getBinaries().withType(JarBinarySpec.class).isEmpty();
        }

        private LibrarySpec getSingleMatchingLibrary() {
            if (libsMatchingRequirements.size() == 1) {
                return libsMatchingRequirements.values().iterator().next();
            }
            return null;
        }

        private void resolve(String libraryName) {
            if (libraryName == null) {
                LibrarySpec singleMatchingLibrary = getSingleMatchingLibrary();
                if (singleMatchingLibrary == null) {
                    return;
                }
                libraryName = singleMatchingLibrary.getName();
            }

            selectedLibrary = libsMatchingRequirements.get(libraryName);
            nonMatchingLibrary = libsNotMatchingRequirements.get(libraryName);
        }

        public boolean isProjectNotFound() {
            return PROJECT_NOT_FOUND == this;
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
