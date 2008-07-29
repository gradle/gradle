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

import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.Project;
import org.gradle.api.UnknownDependencyNotation;

import java.util.HashSet;
import java.util.Set;

/**
* @author Hans Dockter
*/
public abstract class AbstractDependency implements Dependency {
    private Set<String> confs = new HashSet();

    private Object userDependencyDescription;

    private Project project;

    public AbstractDependency(Set confs, Object userDependencyDescription, Project project) {
        boolean valid = true;
        if (!(isValidType(userDependencyDescription)) || !isValidDescription(userDependencyDescription)) {
            throw new UnknownDependencyNotation("Description " + userDependencyDescription + " not valid!");
        }
        this.confs = confs;
        this.userDependencyDescription = userDependencyDescription;
        this.project = project;
    }

    public abstract boolean isValidDescription(Object userDependencyDescription);

    public abstract Class[] userDepencencyDescriptionType();

    public ModuleRevisionId createModuleRevisionId(String org, String name, String version) {
        return new ModuleRevisionId(new ModuleId(org, name), null, version);
    }

    private boolean isValidType(Object userDependencyDescription) {
        for (Class clazz : userDepencencyDescriptionType()) {
            if (clazz.isAssignableFrom(userDependencyDescription.getClass())) {
                return true; 
            }
        }
        return false;
    }

    public void initialize() {}

    public Set<String> getConfs() {
        return confs;
    }

    public void setConfs(Set confs) {
        this.confs = confs;
    }

    public Object getUserDependencyDescription() {
        return userDependencyDescription;
    }

    public void setUserDependencyDescription(Object userDependencyDescription) {
        this.userDependencyDescription = userDependencyDescription;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }
}
