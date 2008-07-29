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

package org.gradle.api.dependencies;

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.gradle.api.Project;
import org.gradle.util.WrapUtil;

import java.util.Set;

/**
* @author Hans Dockter
*/
public class ProjectDependency extends AbstractDependency {
    private String dependencyConfiguration = Dependency.DEFAULT_CONFIGURATION;

    public ProjectDependency(Project dependencyProject, String dependencyConfiguration) {
        super(null, dependencyProject, null);
        this.dependencyConfiguration = dependencyConfiguration;
    }

    public ProjectDependency(Set confs, Object userDependencyDescription, Project project) {
        super(confs, userDependencyDescription, project);
    }

    public boolean isValidDescription(Object userDependencyDescription) {
        return true;
    }

    public Class[] userDepencencyDescriptionType() {
        return WrapUtil.toArray(Project.class);
    }

    public DependencyDescriptor createDepencencyDescriptor() {
        DefaultDependencyDescriptor dd = new DefaultDependencyDescriptor(getDependencyProject().getDependencies().createModuleRevisionId(), false, true);
        for (String conf : getConfs()) {
            dd.addDependencyConfiguration(conf, dependencyConfiguration);
        }
        return dd;
    }

    public ProjectDependency dependencyConfiguration(String dependencyConfiguration) {
        this.dependencyConfiguration = dependencyConfiguration;
        return this;
    }

    public Project getDependencyProject() {
        return (Project) getUserDependencyDescription();
    }

    public void initialize() {
        for (String conf : getConfs()) {
            for (String taskName : getProject().getDependencies().getTasks4Conf().get(conf)) {
                 getDependencyProject().evaluate();
                    getProject().task(taskName).dependsOn(getDependencyProject().task(getDependencyProject().getDependencies().getArtifactProductionTaskName()).getPath());
            }
        }
    }

    public String getDependencyConfiguration() {
        return dependencyConfiguration;
    }

    public void setDependencyConfiguration(String dependencyConfiguration) {
        this.dependencyConfiguration = dependencyConfiguration;
    }
}