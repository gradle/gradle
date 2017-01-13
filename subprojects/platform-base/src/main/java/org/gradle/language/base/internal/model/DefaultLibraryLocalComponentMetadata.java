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

import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.attributes.DefaultAttributesSchema;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.local.model.DefaultLibraryComponentSelector;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.gradle.internal.component.model.ComponentAttributeMatcher;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalComponentDependencyMetadata;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.LibraryBinaryDependencySpec;
import org.gradle.platform.base.ModuleDependencySpec;
import org.gradle.platform.base.ProjectDependencySpec;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static org.gradle.platform.base.internal.DefaultModuleDependencySpec.effectiveVersionFor;

public class DefaultLibraryLocalComponentMetadata extends DefaultLocalComponentMetadata {
    private static final String VERSION = "<local component>";
    private static final List<Exclude> EXCLUDE_RULES = Collections.emptyList();
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
                Collections.singleton(usage),
                true,
                true,
                ImmutableAttributes.EMPTY,
                true,
                false);
        }
        return metaData;
    }

    private static void addDependenciesToMetadata(DefaultLibraryLocalComponentMetadata metadata, String defaultProject, Iterable<DependencySpec> value, String configuration) {
        metadata.addDependencies(value, defaultProject, configuration);
    }

    private static DefaultModuleVersionIdentifier localModuleVersionIdentifierFor(LibraryBinaryIdentifier componentId) {
        return new DefaultModuleVersionIdentifier(componentId.getProjectPath(), componentId.getLibraryName(), VERSION);
    }

    private DefaultLibraryLocalComponentMetadata(ModuleVersionIdentifier id, ComponentIdentifier componentIdentifier) {
        super(id, componentIdentifier, Project.DEFAULT_STATUS, new DefaultAttributesSchema(new ComponentAttributeMatcher()));
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
        addDependency(metadata);
    }

    private LocalOriginDependencyMetadata moduleDependencyMetadata(ModuleDependencySpec moduleDependency, String usageConfigurationName) {
        ModuleVersionSelector requested = moduleVersionSelectorFrom(moduleDependency);
        ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(requested);
        // TODO: This hard-codes the assumption of a 'compile' configuration on the external module
        // Instead, we should be creating an API configuration for each resolved module
        return dependencyMetadataFor(selector, requested, usageConfigurationName, CONFIGURATION_COMPILE);
    }

    // TODO: projectDependency should be transformed based on defaultProject (and other context) elsewhere.
    private LocalOriginDependencyMetadata projectDependencyMetadata(ProjectDependencySpec projectDependency, String defaultProject, String usageConfigurationName) {
        String projectPath = projectDependency.getProjectPath();
        if (isNullOrEmpty(projectPath)) {
            projectPath = defaultProject;
        }
        String libraryName = projectDependency.getLibraryName();
        ComponentSelector selector = new DefaultLibraryComponentSelector(projectPath, libraryName);
        DefaultModuleVersionSelector requested = new DefaultModuleVersionSelector(nullToEmpty(projectPath), nullToEmpty(libraryName), getId().getVersion());
        return dependencyMetadataFor(selector, requested, usageConfigurationName, usageConfigurationName);
    }

    private LocalOriginDependencyMetadata binaryDependencyMetadata(LibraryBinaryDependencySpec binarySpec, String usageConfigurationName) {
        String projectPath = binarySpec.getProjectPath();
        String libraryName = binarySpec.getLibraryName();
        ComponentSelector selector = new DefaultLibraryComponentSelector(projectPath, libraryName, binarySpec.getVariant());
        DefaultModuleVersionSelector requested = new DefaultModuleVersionSelector(projectPath, libraryName, getId().getVersion());
        return dependencyMetadataFor(selector, requested, usageConfigurationName, usageConfigurationName);
    }

    private ModuleVersionSelector moduleVersionSelectorFrom(ModuleDependencySpec module) {
        return new DefaultModuleVersionSelector(module.getGroup(), module.getName(), effectiveVersionFor(module.getVersion()));
    }

    /**
     * This generates local dependency metadata for a dependency, but with a specific trick: normally, "usage" represents
     * the kind of dependency which is requested. For example, a library may require the API of a component, or the runtime of a component.
     * However, for external dependencies, there's no configuration called 'API' or 'runtime': we're mapping them to 'compile', which is
     * assumed to exist. Therefore, this method takes 2 arguments: one is the requested usage ("API") and the other is the mapped usage
     * ("compile"). For local libraries, both should be equal, but for external dependencies, they will be different.
     */
    private LocalOriginDependencyMetadata dependencyMetadataFor(ComponentSelector selector, ModuleVersionSelector requested, String usageConfigurationName, String mappedUsageConfiguration) {
        return new LocalComponentDependencyMetadata(
                selector, requested, usageConfigurationName, null, mappedUsageConfiguration,
                Collections.<IvyArtifactName>emptySet(),
                EXCLUDE_RULES,
                false, false, true);
    }

}
