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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter;

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.plugins.conflict.LatestConflictManager;
import org.apache.ivy.plugins.latest.LatestRevisionStrategy;
import org.apache.ivy.plugins.matcher.ExactPatternMatcher;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.artifacts.DependencyContainerInternal;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.specs.Spec;

/**
 * @author Hans Dockter
 */
public class DefaultDependenciesToModuleDescriptorConverter implements DependenciesToModuleDescriptorConverter {
    public void addDependencyDescriptors(DefaultModuleDescriptor moduleDescriptor, DependencyContainerInternal dependencyContainer,
                                          Spec spec) {
        addDependencies(moduleDescriptor, dependencyContainer, spec);
        addExcludeRules(moduleDescriptor, dependencyContainer);
        addConflictManager(moduleDescriptor);
    }

    private void addDependencies(DefaultModuleDescriptor moduleDescriptor, DependencyContainerInternal dependencyContainer, Spec<Dependency> spec) {
        for (Dependency dependency : dependencyContainer.getDependencies(spec)) {
            moduleDescriptor.addDependency(dependency.createDependencyDescriptor(moduleDescriptor));
        }
    }

    private void addExcludeRules(DefaultModuleDescriptor moduleDescriptor, DependencyContainerInternal dependencyContainer) {
        for (ExcludeRule excludeRule : dependencyContainer.getExcludeRules().createRules(IvyUtil.getAllMasterConfs(moduleDescriptor.getConfigurations()))) {
            moduleDescriptor.addExcludeRule(excludeRule);
        }
    }

    private void addConflictManager(DefaultModuleDescriptor moduleDescriptor) {
        moduleDescriptor.addConflictManager(new ModuleId(ExactPatternMatcher.ANY_EXPRESSION,
                ExactPatternMatcher.ANY_EXPRESSION), ExactPatternMatcher.INSTANCE,
                new LatestConflictManager(new LatestRevisionStrategy()));
    }
}
