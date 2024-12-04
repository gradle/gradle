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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.capability.DefaultSpecificCapabilitySelector;
import org.gradle.api.internal.artifacts.capability.FeatureCapabilitySelector;
import org.gradle.api.internal.artifacts.capability.SpecificCapabilitySelector;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.internal.component.external.model.ProjectDerivedCapability;
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionByNameException;
import org.gradle.internal.component.resolution.failure.type.ConfigurationNotConsumableFailure;
import org.gradle.internal.deprecation.DeprecatableConfiguration;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.util.Path;
import org.gradle.util.internal.GUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DefaultProjectDependency extends AbstractModuleDependency implements ProjectDependencyInternal {
    private final ProjectInternal dependencyProject;
    private final boolean buildProjectDependencies;
    private final TaskDependencyFactory taskDependencyFactory;

    @SuppressWarnings("unused") // Called reflectively by instantiator
    public DefaultProjectDependency(ProjectInternal dependencyProject, boolean buildProjectDependencies, TaskDependencyFactory taskDependencyFactory) {
        this(dependencyProject, null, buildProjectDependencies, taskDependencyFactory);
    }

    public DefaultProjectDependency(ProjectInternal dependencyProject, boolean buildProjectDependencies) {
        this(dependencyProject, null, buildProjectDependencies, DefaultTaskDependencyFactory.withNoAssociatedProject());
    }

    public DefaultProjectDependency(ProjectInternal dependencyProject, @Nullable String configuration, boolean buildProjectDependencies, TaskDependencyFactory taskDependencyFactory) {
        super(configuration);
        this.dependencyProject = dependencyProject;
        this.buildProjectDependencies = buildProjectDependencies;
        this.taskDependencyFactory = taskDependencyFactory;
    }

    @Override
    public String getPath() {
        return dependencyProject.getPath();
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
    public Path getIdentityPath() {
        return dependencyProject.getIdentityPath();
    }

    @Override
    public ProjectIdentity getTargetProjectIdentity() {
        return dependencyProject.getOwner().getIdentity();
    }

    private Configuration findProjectConfiguration() {
        ConfigurationContainer dependencyConfigurations = DeprecationLogger.whileDisabled(() -> getDependencyProject().getConfigurations());
        String declaredConfiguration = getTargetConfiguration();
        Configuration selectedConfiguration = dependencyConfigurations.getByName(GUtil.isTrue(declaredConfiguration) ? declaredConfiguration : Dependency.DEFAULT_CONFIGURATION);
        if (!selectedConfiguration.isCanBeConsumed()) {
            failDueToNonConsumableConfigurationSelection(selectedConfiguration);
        }
        ((DeprecatableConfiguration) selectedConfiguration).maybeEmitConsumptionDeprecation();
        return selectedConfiguration;
    }

    /**
     * Fails the resolution of the project dependency because the selected configuration is not consumable by
     * throwing the appropriate exception.
     *
     * This method is kind of ugly.  If we could get a hold of the ResolutionFailureHandler in this class, we could use the typical
     * failure handling mechanism.  But we can't, so we have to throw the exception ourselves.  And as the describer
     * for this failure is abstract, we need to create an anonymous instance of it ourselves here, since there are
     * no instantiator types available here.
     *
     * NOTE: This should all be going away in Gradle 9, so it's okay to remain ugly for a little while.
     *
     * @param selectedConfiguration the non-consumable configuration that was selected
     */
    private void failDueToNonConsumableConfigurationSelection(Configuration selectedConfiguration) {
        ConfigurationNotConsumableFailure failure = new ConfigurationNotConsumableFailure(dependencyProject.getOwner().getComponentIdentifier(), selectedConfiguration.getName());
        String message = String.format(
            "Selected configuration '" + failure.getRequestedConfigurationName() + "' on " + failure.getTargetComponent().getDisplayName() +
            " but it can't be used as a project dependency because it isn't intended for consumption by other components."
        );
        throw new VariantSelectionByNameException(message, failure, Collections.emptyList());
    }

    @Override
    public ProjectDependency copy() {
        DefaultProjectDependency copiedProjectDependency = new DefaultProjectDependency(dependencyProject, getTargetConfiguration(), buildProjectDependencies, taskDependencyFactory);
        copyTo(copiedProjectDependency);
        return copiedProjectDependency;
    }

    @Override
    @Deprecated
    public Set<File> resolve() {
        return resolve(true);
    }

    @Override
    @Deprecated
    public Set<File> resolve(boolean transitive) {

        DeprecationLogger.deprecate("Directly resolving the files of project dependency '" + getIdentityPath() + "'")
            .withAdvice("Add the dependency to a resolvable configuration and resolve the configuration.")
            .willBecomeAnErrorInGradle9()
            .withUpgradeGuideSection(8, "deprecate_self_resolving_dependency")
            .nagUser();

        org.gradle.api.internal.artifacts.CachingDependencyResolveContext context =
            new org.gradle.api.internal.artifacts.CachingDependencyResolveContext(taskDependencyFactory, transitive, Collections.emptyMap());
        context.add(this);
        return context.resolve().getFiles();
    }

    @Override
    @Deprecated
    @SuppressWarnings("deprecation")
    public void resolve(org.gradle.api.internal.artifacts.CachingDependencyResolveContext context) {
        boolean transitive = isTransitive() && context.isTransitive();
        if (transitive) {
            Configuration projectConfiguration = findProjectConfiguration();
            for (Dependency dependency : projectConfiguration.getAllDependencies()) {
                context.add(dependency);
            }
            for (DependencyConstraint dependencyConstraint : projectConfiguration.getAllDependencyConstraints()) {
                context.add(dependencyConstraint);
            }
        }
    }

    @Override
    @Deprecated
    public TaskDependencyInternal getBuildDependencies() {

        DeprecationLogger.deprecate("Accessing the build dependencies of project dependency '" + getIdentityPath() + "'")
            .withAdvice("Add the dependency to a resolvable configuration and use the configuration to track task dependencies.")
            .willBecomeAnErrorInGradle9()
            .withUpgradeGuideSection(8, "deprecate_self_resolving_dependency")
            .nagUser();

        return taskDependencyFactory.visitingDependencies(context -> {
            if (!buildProjectDependencies) {
                return;
            }

            Configuration configuration = findProjectConfiguration();
            context.add(configuration);
            context.add(configuration.getAllArtifacts());
        });
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
    @Deprecated
    public boolean contentEquals(Dependency dependency) {

        DeprecationLogger.deprecateMethod(Dependency.class, "contentEquals(Dependency)")
            .withAdvice("Use Object.equals(Object) instead")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecated_content_equals")
            .nagUser();

        if (this == dependency) {
            return true;
        }
        if (getClass() != dependency.getClass()) {
            return false;
        }

        DefaultProjectDependency that = (DefaultProjectDependency) dependency;
        if (!isCommonContentEquals(that)) {
            return false;
        }

        return getIdentityPath().equals(that.getIdentityPath());
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
        if (!this.getIdentityPath().equals(that.getIdentityPath())) {
            return false;
        }
        if (getTargetConfiguration() != null ? !this.getTargetConfiguration().equals(that.getTargetConfiguration())
            : that.getTargetConfiguration() != null) {
            return false;
        }
        if (this.buildProjectDependencies != that.buildProjectDependencies) {
            return false;
        }
        if (!Objects.equal(getAttributes(), that.getAttributes())) {
            return false;
        }
        if (!Objects.equal(getCapabilitySelectors(), that.getCapabilitySelectors())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hashCode = getIdentityPath().hashCode();
        if (getTargetConfiguration() != null) {
            hashCode = 31 * hashCode + getTargetConfiguration().hashCode();
        }
        return hashCode;
    }

    @Override
    public String toString() {
        return "project '" + dependencyProject.getBuildTreePath() + "'";
    }
}
