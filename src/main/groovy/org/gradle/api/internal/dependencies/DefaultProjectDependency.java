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
import org.gradle.api.dependencies.ProjectDependency;
import org.gradle.api.dependencies.Dependency;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;

import java.util.HashSet;
import java.util.Set;

/**
* @author Hans Dockter
*/
public class DefaultProjectDependency extends AbstractDependency implements ProjectDependency, Dependency {
    private Project project;

    private Project dependencyProject;

    private boolean transitive = true;

    public DefaultProjectDependency(Set confs, Object dependencyProject, Project project) {
        super(confs, dependencyProject);
        this.project = project;
        this.dependencyProject = (Project) dependencyProject;
    }

    public boolean isValidDescription(Object userDependencyDescription) {
        return true;
    }

    public Class[] userDepencencyDescriptionType() {
        return WrapUtil.toArray(Project.class);
    }

    public DependencyDescriptor createDependencyDescriptor(ModuleDescriptor parent) {
        return getDependencyDescriptorFactory().createFromProjectDependency(parent, this);
    }

    public Project getDependencyProject() {
        return dependencyProject;
    }

    public void initialize() {
        for (String conf : getDependencyConfigurationMappings().getMasterConfigurations()) {
            Set<String> tasks = GUtil.elvis(getProject().getDependencies().getTasks4Conf().get(conf), new HashSet<String>()); 
            for (String taskName : tasks) {
                ((ProjectInternal) getDependencyProject()).evaluate();
                getProject().task(taskName).dependsOn(getDependencyProject().task(getDependencyProject().getDependencies().getArtifactProductionTaskName()).getPath());
            }
        }
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
}