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

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.Project;
import org.gradle.api.dependencies.DependencyConfigurationMappingContainer;
import org.gradle.api.dependencies.ProjectDependency;
import org.gradle.api.internal.dependencies.ivyservice.DefaultDependencyDescriptorFactory;
import org.gradle.api.internal.dependencies.ivyservice.DependencyDescriptorFactory;
import org.gradle.util.WrapUtil;

/**
* @author Hans Dockter
*/
public class DefaultProjectDependency extends AbstractDescriptorDependency implements ProjectDependency {
    private Project project;

    private Project dependencyProject;

    private boolean transitive = true;
    private DependencyDescriptorFactory dependencyDescriptorFactory = new DefaultDependencyDescriptorFactory();

    public DefaultProjectDependency(DependencyConfigurationMappingContainer dependencyConfigurationMappings, Object dependencyProject, Project project) {
        super(dependencyConfigurationMappings, dependencyProject);
        this.project = project;
        this.dependencyProject = (Project) dependencyProject;
        this.dependencyProject = (Project) dependencyProject;
    }

    public boolean isValidDescription(Object userDependencyDescription) {
        return true;
    }

    public Class[] userDepencencyDescriptionType() {
        return WrapUtil.toArray(Project.class);
    }

    public DependencyDescriptor createDependencyDescriptor(ModuleDescriptor parent) {
        return getTransformer().transform(getDependencyDescriptorFactory().createFromProjectDependency(parent, this));
    }

    public Project getDependencyProject() {
        return dependencyProject;
    }
    
    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public boolean isTransitive() {
        return transitive;
    }

    public DefaultProjectDependency setTransitive(boolean transitive) {
        this.transitive = transitive;
        return this;
    }

    public String getGroup() {
        return dependencyProject.getGroup().toString();
    }

    public String getName() {
        return dependencyProject.getName();
    }

    public String getVersion() {
        return dependencyProject.getVersion().toString();
    }
   
    public DependencyDescriptorFactory getDependencyDescriptorFactory() {
        return dependencyDescriptorFactory;
    }

    public void setDependencyDescriptorFactory(DependencyDescriptorFactory dependencyDescriptorFactory) {
        this.dependencyDescriptorFactory = dependencyDescriptorFactory;
    }
}