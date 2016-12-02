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

import org.gradle.api.Nullable;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.dependencies.SelfResolvingDependencyInternal;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.component.local.model.BuildableLocalComponentMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;

public class DefaultDependenciesToModuleDescriptorConverter implements DependenciesToModuleDescriptorConverter {
    private DependencyDescriptorFactory dependencyDescriptorFactory;
    private ExcludeRuleConverter excludeRuleConverter;

    public DefaultDependenciesToModuleDescriptorConverter(DependencyDescriptorFactory dependencyDescriptorFactory,
                                                          ExcludeRuleConverter excludeRuleConverter) {
        this.dependencyDescriptorFactory = dependencyDescriptorFactory;
        this.excludeRuleConverter = excludeRuleConverter;
    }

    @Override
    public void addDependencyDescriptors(BuildableLocalComponentMetadata metaData, ConfigurationInternal configuration) {
        addDependencies(metaData, configuration);
        addExcludeRules(metaData, configuration);
    }

    private void addDependencies(BuildableLocalComponentMetadata metaData, ConfigurationInternal configuration) {
        AttributeContainerInternal attributes = configuration.getAttributes().asImmutable();
        for (Dependency dependency : configuration.getDependencies()) {
            if (dependency instanceof ModuleDependency) {
                ModuleDependency moduleDependency = (ModuleDependency) dependency;
                metaData.addDependency(dependencyDescriptorFactory.createDependencyDescriptor(configuration.getName(), attributes, moduleDependency));
            } else if (dependency instanceof FileCollectionDependency) {
                final FileCollectionDependency fileDependency = (FileCollectionDependency) dependency;
                metaData.addFiles(configuration.getName(), new DefaultLocalFileDependencyMetadata(fileDependency));
            } else {
                throw new IllegalArgumentException("Cannot convert dependency " + dependency + " to local component dependency metadata.");
            }
        }
    }

    private void addExcludeRules(BuildableLocalComponentMetadata metaData, ConfigurationInternal configuration) {
        for (ExcludeRule excludeRule : configuration.getExcludeRules()) {
            metaData.addExclude(excludeRuleConverter.convertExcludeRule(configuration.getName(), excludeRule));
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
        public FileCollection getFiles() {
            return fileDependency.getFiles();
        }
    }
}
