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

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.util.WrapUtil;

import java.util.List;

public class DefaultDependencyDescriptorFactory implements DependencyDescriptorFactory {
    private List<IvyDependencyDescriptorFactory> dependencyDescriptorFactories;

    public DefaultDependencyDescriptorFactory(IvyDependencyDescriptorFactory... dependencyDescriptorFactories) {
        this.dependencyDescriptorFactories = WrapUtil.toList(dependencyDescriptorFactories);
    }

    public DependencyMetaData createDependencyDescriptor(String configuration, ModuleDescriptor moduleDescriptor, ModuleDependency dependency) {
        IvyDependencyDescriptorFactory factoryInternal = findFactoryForDependency(dependency);
        return factoryInternal.createDependencyDescriptor(configuration, dependency, moduleDescriptor);
    }

    private IvyDependencyDescriptorFactory findFactoryForDependency(ModuleDependency dependency) {
        for (IvyDependencyDescriptorFactory ivyDependencyDescriptorFactory : dependencyDescriptorFactories) {
            if (ivyDependencyDescriptorFactory.canConvert(dependency)) {
                return ivyDependencyDescriptorFactory;
            }
        }
        throw new InvalidUserDataException("Can't map dependency of type: " + dependency.getClass());
    }
}
