/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.dependencies;

import org.apache.ivy.core.module.descriptor.*;
import org.gradle.api.DependencyManager;
import org.gradle.api.dependencies.Dependency;
import org.gradle.api.dependencies.GradleArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Hans Dockter
 */
public class ModuleDescriptorConverter {
    private static Logger logger = LoggerFactory.getLogger(ModuleDescriptorConverter.class);

    ModuleDescriptorConverter() {
    }

    ModuleDescriptor convert(BaseDependencyManager dependencyManager) {
        String status = DependencyManager.DEFAULT_STATUS;
        if (dependencyManager.getProject().hasProperty("status")) {
            status = (String) dependencyManager.getProject().property("status");
        }
        DefaultModuleDescriptor moduleDescriptor = new DefaultModuleDescriptor(dependencyManager.createModuleRevisionId(),
                status, null);
        for (Configuration configuration : dependencyManager.getConfigurations().values()) {
            moduleDescriptor.addConfiguration(configuration);
        }
        addDependencyDescriptors(moduleDescriptor, dependencyManager);
        addArtifacts(moduleDescriptor, dependencyManager);
        addExcludes(moduleDescriptor, dependencyManager);
        return moduleDescriptor;
    }

    private void addExcludes(DefaultModuleDescriptor moduleDescriptor, BaseDependencyManager dependencyManager) {
        for (ExcludeRule excludeRule : dependencyManager.getExcludeRules().getRules()) {
            moduleDescriptor.addExcludeRule(excludeRule);
        }
    }

    private void addDependencyDescriptors(DefaultModuleDescriptor moduleDescriptor, BaseDependencyManager dependencyManager) {
        for (Dependency dependency : dependencyManager.getDependencies()) {
            moduleDescriptor.addDependency(dependency.createDepencencyDescriptor());
        }
        for (DependencyDescriptor dependencyDescriptor : dependencyManager.getDependencyDescriptors()) {
            moduleDescriptor.addDependency(dependencyDescriptor);
        }
    }

    private void addArtifacts(DefaultModuleDescriptor moduleDescriptor, BaseDependencyManager dependencyManager) {
        for (String conf : dependencyManager.getArtifacts().keySet()) {
            List<GradleArtifact> gradleArtifacts = dependencyManager.getArtifacts().get(conf);
            for (GradleArtifact gradleArtifact : gradleArtifacts) {
                logger.debug("Add gradleArtifact: {} to configuration={}", gradleArtifact, conf);
                moduleDescriptor.addArtifact(conf, gradleArtifact.createIvyArtifact(dependencyManager.createModuleRevisionId()));
            }
        }
        for (String conf : dependencyManager.getArtifactDescriptors().keySet()) {
            List<Artifact> artifacts = dependencyManager.getArtifactDescriptors().get(conf);
            for (Artifact artifact : artifacts) {
                moduleDescriptor.addArtifact(conf, artifact);
            }
        }
    }
}
