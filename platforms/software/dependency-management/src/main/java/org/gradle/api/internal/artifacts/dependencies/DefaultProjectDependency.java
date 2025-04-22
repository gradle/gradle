/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.artifacts.dependencies;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.capability.DefaultSpecificCapabilitySelector;
import org.gradle.api.internal.artifacts.capability.FeatureCapabilitySelector;
import org.gradle.api.internal.artifacts.capability.SpecificCapabilitySelector;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.component.external.model.ProjectDerivedCapability;
import org.gradle.internal.deprecation.DeprecationLogger;

import java.util.List;

public class DefaultProjectDependency extends AbstractModuleDependency implements ProjectDependencyInternal {

    private final ProjectInternal dependencyProject;

    public DefaultProjectDependency(ProjectInternal dependencyProject) {
        this.dependencyProject = dependencyProject;
    }

    @Override
    public String getPath() {
        return getTargetProjectIdentity().getProjectPath().toString();
    }

    @Override
    @Deprecated
    public Project getDependencyProject() {
        DeprecationLogger.deprecateMethod(ProjectDependency.class, "getDependencyProject()")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecate_get_dependency_project")
            .nagUser();

        return dependencyProject;
    }

    @Override
    public String getGroup() {
        return dependencyProject.getGroup().toString();
    }

    @Override
    public String getName() {
        return dependencyProject.getName();
    }

    @Override
    public String getVersion() {
        return dependencyProject.getVersion().toString();
    }

    @Override
    public ProjectIdentity getTargetProjectIdentity() {
        return dependencyProject.getOwner().getIdentity();
    }

    @Override
    public ProjectDependency copy() {
        DefaultProjectDependency copiedProjectDependency = new DefaultProjectDependency(dependencyProject);
        copyTo(copiedProjectDependency);
        return copiedProjectDependency;
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<Capability> getRequestedCapabilities() {
        return getCapabilitySelectors().stream()
            .map(c -> {
                if (c instanceof SpecificCapabilitySelector) {
                    return ((DefaultSpecificCapabilitySelector) c).getBackingCapability();
                } else if (c instanceof FeatureCapabilitySelector) {
                    return new ProjectDerivedCapability(dependencyProject, ((FeatureCapabilitySelector) c).getFeatureName());
                } else {
                    throw new UnsupportedOperationException("Unsupported capability selector type: " + c.getClass().getName());
                }
            })
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultProjectDependency that = (DefaultProjectDependency) o;
        return getTargetProjectIdentity().equals(that.getTargetProjectIdentity()) &&
            isCommonContentEquals(that);
    }

    @Override
    public int hashCode() {
        int hashCode = getTargetProjectIdentity().hashCode();
        if (getTargetConfiguration() != null) {
            hashCode = 31 * hashCode + getTargetConfiguration().hashCode();
        }
        return hashCode;
    }

    @Override
    public String toString() {
        return "project '" + getTargetProjectIdentity().getBuildTreePath() + "'";
    }

}
