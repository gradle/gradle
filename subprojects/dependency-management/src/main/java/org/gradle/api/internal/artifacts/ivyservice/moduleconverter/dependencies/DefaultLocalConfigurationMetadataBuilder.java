/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.dependencies.SelfResolvingDependencyInternal;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.internal.component.local.model.BuildableLocalConfigurationMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;

import javax.annotation.Nullable;

public class DefaultLocalConfigurationMetadataBuilder implements LocalConfigurationMetadataBuilder {
    private final DependencyDescriptorFactory dependencyDescriptorFactory;
    private final ExcludeRuleConverter excludeRuleConverter;

    public DefaultLocalConfigurationMetadataBuilder(DependencyDescriptorFactory dependencyDescriptorFactory,
                                                    ExcludeRuleConverter excludeRuleConverter) {
        this.dependencyDescriptorFactory = dependencyDescriptorFactory;
        this.excludeRuleConverter = excludeRuleConverter;
    }

    @Override
    public void addDependenciesAndExcludes(BuildableLocalConfigurationMetadata metaData, ConfigurationInternal configuration) {
        // Run any actions to add/modify dependencies
        configuration.runDependencyActions();

        addDependencies(metaData, configuration);
        addDependencyConstraints(metaData, configuration);
        addExcludeRules(metaData, configuration);
    }

    private void addDependencies(BuildableLocalConfigurationMetadata configurationMetadata, ConfigurationInternal configuration) {
        AttributeContainerInternal attributes = configuration.getAttributes();
        for (Dependency dependency : configuration.getDependencies()) {
            if (dependency instanceof ModuleDependency) {
                ModuleDependency moduleDependency = (ModuleDependency) dependency;
                configurationMetadata.addDependency(dependencyDescriptorFactory.createDependencyDescriptor(configurationMetadata.getComponentId(), configuration.getName(), attributes, moduleDependency));
            } else if (dependency instanceof FileCollectionDependency) {
                final FileCollectionDependency fileDependency = (FileCollectionDependency) dependency;
                configurationMetadata.addFiles(new DefaultLocalFileDependencyMetadata(fileDependency));
            } else {
                throw new IllegalArgumentException("Cannot convert dependency " + dependency + " to local component dependency metadata.");
            }
        }
    }

    private void addDependencyConstraints(BuildableLocalConfigurationMetadata configurationMetadata, ConfigurationInternal configuration) {
        AttributeContainerInternal attributes = configuration.getAttributes();
        for (DependencyConstraint dependencyConstraint : configuration.getDependencyConstraints()) {
            configurationMetadata.addDependency(dependencyDescriptorFactory.createDependencyConstraintDescriptor(configurationMetadata.getComponentId(), configuration.getName(), attributes, dependencyConstraint));
        }
    }

    private void addExcludeRules(BuildableLocalConfigurationMetadata configurationMetadata, ConfigurationInternal configuration) {
        for (ExcludeRule excludeRule : configuration.getExcludeRules()) {
            configurationMetadata.addExclude(excludeRuleConverter.convertExcludeRule(excludeRule));
        }
    }

    private static class DefaultLocalFileDependencyMetadata implements LocalFileDependencyMetadata {
        private final FileCollectionDependency fileDependency;

        DefaultLocalFileDependencyMetadata(FileCollectionDependency fileDependency) {
            this.fileDependency = fileDependency;
        }

        @Override
        public FileCollectionDependency getSource() {
            return fileDependency;
        }

        @Override @Nullable
        public ComponentIdentifier getComponentId() {
            return ((SelfResolvingDependencyInternal) fileDependency).getTargetComponentId();
        }

        @Override
        public FileCollectionInternal getFiles() {
            return (FileCollectionInternal) fileDependency.getFiles();
        }
    }
}
