/*
 * Copyright 2007-2008 the original author or authors.
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

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.util.WrapUtil;

import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DependencyDescriptorFactoryDelegate implements DependencyDescriptorFactory {
    private Set<DependencyDescriptorFactoryInternal> dependencyDescriptorFactories;

    public DependencyDescriptorFactoryDelegate(DependencyDescriptorFactoryInternal... dependencyDescriptorFactories) {
        this.dependencyDescriptorFactories = WrapUtil.toSet(dependencyDescriptorFactories);
    }

    public void addDependencyDescriptor(String configuration, DefaultModuleDescriptor moduleDescriptor,
                                        ModuleDependency dependency) {
        DependencyDescriptorFactoryInternal factoryInternal = findFactoryForDependency(dependency);
        factoryInternal.addDependencyDescriptor(configuration, moduleDescriptor, dependency);
    }

    public ModuleRevisionId createModuleRevisionId(ModuleDependency dependency) {
        DependencyDescriptorFactoryInternal factoryInternal = findFactoryForDependency(dependency);
        return factoryInternal.createModuleRevisionId(dependency);
    }

    private DependencyDescriptorFactoryInternal findFactoryForDependency(ModuleDependency dependency) {
        for (DependencyDescriptorFactoryInternal dependencyDescriptorFactoryInternal : dependencyDescriptorFactories) {
            if (dependencyDescriptorFactoryInternal.canConvert(dependency)) {
                return dependencyDescriptorFactoryInternal;
            }
        }
        throw new InvalidUserDataException("Can't map dependency of type: " + dependency.getClass());
    }
}
