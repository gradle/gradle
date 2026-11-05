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
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.capability.DefaultSpecificCapabilitySelector;
import org.gradle.api.internal.artifacts.capability.FeatureCapabilitySelector;
import org.gradle.api.internal.artifacts.capability.SpecificCapabilitySelector;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.component.external.model.ProjectDerivedCapability;
import org.gradle.internal.configuration.problems.IsolatedProjectsProblemsReporter;
import org.gradle.internal.configuration.problems.StructuredMessage;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class DefaultProjectDependency extends AbstractModuleDependency implements ProjectDependencyInternal {

    private final ProjectState projectState;
    private final @Nullable ProjectIdentity referrer;
    private final IsolatedProjectsProblemsReporter ipProblems;

    /**
     * @param referrer the project that declares this dependency, if any
     */
    public DefaultProjectDependency(ProjectState projectState, @Nullable ProjectIdentity referrer, IsolatedProjectsProblemsReporter ipProblems) {
        this.projectState = projectState;
        this.referrer = referrer;
        this.ipProblems = ipProblems;
    }

    @Override
    public String getPath() {
        return getTargetProjectIdentity().getProjectPath().asString();
    }

    @Override
    public String getGroup() {
        return accessTargetProject("ProjectDependency.getGroup()", message -> message
            .text(", as the value comes from that project's ").reference("group").text(" property")
        ).getGroup().toString();
    }

    @Override
    public String getName() {
        return getTargetProjectIdentity().getProjectName();
    }

    @Override
    public String getVersion() {
        return accessTargetProject("ProjectDependency.getVersion()", message -> message
            .text(", as the value comes from that project's ").reference("version").text(" property")
        ).getVersion().toString();
    }

    @Override
    public ProjectIdentity getTargetProjectIdentity() {
        return projectState.getIdentity();
    }

    @Override
    public ProjectDependency copy() {
        DefaultProjectDependency copiedProjectDependency = new DefaultProjectDependency(projectState, referrer, ipProblems);
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
                    Project project = accessTargetProject("ModuleDependency.getRequestedCapabilities()", message -> message
                        .text(" that requires a feature, as the capability comes from that project's ")
                        .reference("group").text(" and ").reference("version").text(" properties. Use ")
                        .reference("ModuleDependency.getCapabilitySelectors()").text(" instead")
                    );
                    return new ProjectDerivedCapability(project, ((FeatureCapabilitySelector) c).getFeatureName());
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

    /**
     * Returns the target project's model, reporting cross-project access against {@code api}.
     * <p>
     * Any code which depends on this method should be deprecated and removed.
     * A project dependency should be a simple wrapper around the _identity_ of a given
     * project, and should not retain any reference to the actual project instance.
     */
    @Deprecated
    private Project accessTargetProject(String api, Action<StructuredMessage.Builder> explanation) {
        ProjectIdentity referrer = this.referrer;
        ProjectIdentity target = getTargetProjectIdentity();
        if (referrer != null && !referrer.equals(target)) {
            ipProblems.report(factory ->
                factory.problem("Cross-project model access", message -> {
                    message.text("Project ").reference(referrer.getBuildTreePath())
                        .text(" cannot access ").reference(api)
                        .text(" of a dependency on another project ").reference(target.getBuildTreePath());
                    explanation.execute(message);
                }).build()
            );
        }
        return projectState.getMutableModel();
    }

}
