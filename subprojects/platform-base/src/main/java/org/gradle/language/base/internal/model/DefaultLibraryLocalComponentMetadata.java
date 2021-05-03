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
package org.gradle.language.base.internal.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingState;
import org.gradle.api.internal.attributes.EmptySchema;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.local.model.BuildableLocalConfigurationMetadata;
import org.gradle.internal.component.local.model.DefaultLibraryComponentSelector;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.gradle.internal.component.local.model.RootConfigurationMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalComponentDependencyMetadata;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.deprecation.DeprecationMessageBuilder;
import org.gradle.internal.locking.DefaultDependencyLockingState;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.LibraryBinaryDependencySpec;
import org.gradle.platform.base.ModuleDependencySpec;
import org.gradle.platform.base.ProjectDependencySpec;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.gradle.platform.base.internal.DefaultModuleDependencySpec.effectiveVersionFor;

public class DefaultLibraryLocalComponentMetadata extends DefaultLocalComponentMetadata {
    private static final String VERSION = "<local component>";
    private static final List<ExcludeMetadata> EXCLUDE_RULES = Collections.emptyList();
    private static final String CONFIGURATION_COMPILE = "compile";

    public static DefaultLibraryLocalComponentMetadata newResolvedLibraryMetadata(
        LibraryBinaryIdentifier componentId,
        Map<String, Iterable<DependencySpec>> dependencies,
        String defaultProject) {
        DefaultLibraryLocalComponentMetadata metadata = newDefaultLibraryLocalComponentMetadata(componentId, dependencies.keySet());
        addDependenciesToMetaData(dependencies, metadata, defaultProject);
        return metadata;
    }

    private static void addDependenciesToMetaData(Map<String, Iterable<DependencySpec>> dependencies, DefaultLibraryLocalComponentMetadata metadata, String defaultProject) {
        for (Map.Entry<String, Iterable<DependencySpec>> entry : dependencies.entrySet()) {
            addDependenciesToMetadata(metadata, defaultProject, entry.getValue(), entry.getKey());
        }
    }

    public static DefaultLibraryLocalComponentMetadata newResolvingLocalComponentMetadata(LibraryBinaryIdentifier componentId, String usage, Iterable<DependencySpec> dependencies) {
        DefaultLibraryLocalComponentMetadata metadata = newDefaultLibraryLocalComponentMetadata(componentId, Collections.singleton(usage));
        addDependenciesToMetadata(metadata, componentId.getProjectPath(), dependencies, usage);
        return metadata;
    }

    private static DefaultLibraryLocalComponentMetadata newDefaultLibraryLocalComponentMetadata(LibraryBinaryIdentifier componentId, Set<String> usages) {
        DefaultLibraryLocalComponentMetadata metaData = new DefaultLibraryLocalComponentMetadata(localModuleVersionIdentifierFor(componentId), componentId);
        for (String usage : usages) {
            metaData.addConfiguration(
                usage,
                String.format("Request metadata: %s", componentId.getDisplayName()),
                Collections.<String>emptySet(),
                ImmutableSet.of(usage),
                true,
                true,
                ImmutableAttributes.EMPTY,
                true,
                null,
                false,
                ImmutableCapabilities.EMPTY,
                Collections::emptyList);
        }
        return metaData;
    }

    private static void addDependenciesToMetadata(DefaultLibraryLocalComponentMetadata metadata, String defaultProject, Iterable<DependencySpec> value, String configuration) {
        metadata.addDependencies(value, defaultProject, configuration);
    }

    private static ModuleVersionIdentifier localModuleVersionIdentifierFor(LibraryBinaryIdentifier componentId) {
        return DefaultModuleVersionIdentifier.newId(componentId.getProjectPath(), componentId.getLibraryName(), VERSION);
    }

    private DefaultLibraryLocalComponentMetadata(ModuleVersionIdentifier id, ComponentIdentifier componentIdentifier) {
        super(id, componentIdentifier, Project.DEFAULT_STATUS, EmptySchema.INSTANCE);
    }

    private void addDependencies(Iterable<DependencySpec> dependencies, String projectPath, String usageConfigurationName) {
        for (DependencySpec dependency : dependencies) {
            addDependency(dependency, projectPath, usageConfigurationName);
        }
    }

    private void addDependency(DependencySpec dependency, String defaultProject, String usageConfigurationName) {
        LocalOriginDependencyMetadata metadata = dependency instanceof ModuleDependencySpec
            ? moduleDependencyMetadata((ModuleDependencySpec) dependency, usageConfigurationName)
            : dependency instanceof ProjectDependencySpec ? projectDependencyMetadata((ProjectDependencySpec) dependency, defaultProject, usageConfigurationName)
            : binaryDependencyMetadata((LibraryBinaryDependencySpec) dependency, usageConfigurationName);
        getConfiguration(usageConfigurationName).addDependency(metadata);
    }

    private LocalOriginDependencyMetadata moduleDependencyMetadata(ModuleDependencySpec moduleDependency, String usageConfigurationName) {
        ModuleComponentSelector selector = moduleComponentSelectorFrom(moduleDependency);
        // TODO: This hard-codes the assumption of a 'compile' configuration on the external module
        // Instead, we should be creating an API configuration for each resolved module
        return dependencyMetadataFor(selector, usageConfigurationName, CONFIGURATION_COMPILE);
    }

    // TODO: projectDependency should be transformed based on defaultProject (and other context) elsewhere.
    private LocalOriginDependencyMetadata projectDependencyMetadata(ProjectDependencySpec projectDependency, String defaultProject, String usageConfigurationName) {
        String projectPath = projectDependency.getProjectPath();
        if (isNullOrEmpty(projectPath)) {
            projectPath = defaultProject;
        }
        String libraryName = projectDependency.getLibraryName();
        ComponentSelector selector = new DefaultLibraryComponentSelector(projectPath, libraryName);
        return dependencyMetadataFor(selector, usageConfigurationName, usageConfigurationName);
    }

    private LocalOriginDependencyMetadata binaryDependencyMetadata(LibraryBinaryDependencySpec binarySpec, String usageConfigurationName) {
        String projectPath = binarySpec.getProjectPath();
        String libraryName = binarySpec.getLibraryName();
        ComponentSelector selector = new DefaultLibraryComponentSelector(projectPath, libraryName, binarySpec.getVariant());
        return dependencyMetadataFor(selector, usageConfigurationName, usageConfigurationName);
    }

    private ModuleComponentSelector moduleComponentSelectorFrom(ModuleDependencySpec module) {
        return DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(module.getGroup(), module.getName()), effectiveVersionFor(module.getVersion()));
    }

    /**
     * This generates local dependency metadata for a dependency, but with a specific trick: normally, "usage" represents
     * the kind of dependency which is requested. For example, a library may require the API of a component, or the runtime of a component.
     * However, for external dependencies, there's no configuration called 'API' or 'runtime': we're mapping them to 'compile', which is
     * assumed to exist. Therefore, this method takes 2 arguments: one is the requested usage ("API") and the other is the mapped usage
     * ("compile"). For local libraries, both should be equal, but for external dependencies, they will be different.
     */
    private LocalOriginDependencyMetadata dependencyMetadataFor(ComponentSelector selector, String usageConfigurationName, String mappedUsageConfiguration) {
        return new LocalComponentDependencyMetadata(
            () -> "TODO",
            selector, usageConfigurationName, null, ImmutableAttributes.EMPTY, mappedUsageConfiguration,
                ImmutableList.<IvyArtifactName>of(),
            EXCLUDE_RULES,
            false, false, true, false, false, null);
    }

    @Override
    public BuildableLocalConfigurationMetadata addConfiguration(String name, String description, Set<String> extendsFrom, ImmutableSet<String> hierarchy, boolean visible, boolean transitive, ImmutableAttributes attributes, boolean canBeConsumed, DeprecationMessageBuilder.WithDocumentation consumptionDeprecation, boolean canBeResolved, ImmutableCapabilities capabilities, Supplier<List<DependencyConstraint>> consistentResolutionConstraints) {
        assert hierarchy.contains(name);
        DefaultLocalConfigurationMetadata conf = new LibraryLocalConfigurationMetadata(name, description, visible, transitive, extendsFrom, hierarchy, attributes, canBeConsumed, consumptionDeprecation, canBeResolved, capabilities);
        addToConfigurations(name, conf);
        return conf;
    }

    class LibraryLocalConfigurationMetadata extends DefaultLocalConfigurationMetadata implements RootConfigurationMetadata {

        LibraryLocalConfigurationMetadata(String name,
                                          String description,
                                          boolean visible,
                                          boolean transitive,
                                          Set<String> extendsFrom,
                                          ImmutableSet<String> hierarchy,
                                          ImmutableAttributes attributes,
                                          boolean canBeConsumed,
                                          DeprecationMessageBuilder.WithDocumentation consumptionDeprecation,
                                          boolean canBeResolved,
                                          ImmutableCapabilities capabilities) {
            super(name, description, visible, transitive, extendsFrom, hierarchy, attributes, canBeConsumed, consumptionDeprecation, canBeResolved, capabilities);
        }


        @Override
        public DependencyLockingState getDependencyLockingState() {
            return DefaultDependencyLockingState.EMPTY_LOCK_CONSTRAINT;
        }
    }
}
