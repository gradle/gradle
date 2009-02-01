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
package org.gradle.api.internal.dependencies.ivyservice;

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.dependencies.ClientModule;
import org.gradle.api.dependencies.ModuleDependency;
import org.gradle.api.dependencies.ProjectDependency;

/**
 * @author Hans Dockter
 */
public interface DependencyDescriptorFactory {
    

    DependencyDescriptor createFromModuleDependency(ModuleDescriptor parent, ModuleDependency moduleDependency);

    DependencyDescriptor createFromProjectDependency(ModuleDescriptor parent, ProjectDependency dependency);

    DependencyDescriptor createFromClientModule(ModuleDescriptor parent, ClientModule clientModule);
}
