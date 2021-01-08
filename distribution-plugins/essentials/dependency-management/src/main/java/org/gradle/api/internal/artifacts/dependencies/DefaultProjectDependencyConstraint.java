/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;

import javax.annotation.Nullable;
import java.util.Collections;

/**
 * A limited use, project dependency constraint mostly aimed at publishing
 * platforms.
 */
public class DefaultProjectDependencyConstraint implements DependencyConstraintInternal {
    private final ProjectDependency projectDependency;
    private String reason;
    private boolean force;

    public DefaultProjectDependencyConstraint(ProjectDependency projectDependency) {
        this.projectDependency = projectDependency;
    }

    public ProjectDependency getProjectDependency() {
        return projectDependency;
    }

    @Override
    public void version(Action<? super MutableVersionConstraint> configureAction) {
        throw new UnsupportedOperationException("Cannot change version constraint on a project dependency");
    }

    @Nullable
    @Override
    public String getReason() {
        return reason;
    }

    @Override
    public void because(@Nullable String reason) {
        this.reason = reason;
    }

    @Override
    public AttributeContainer getAttributes() {
        return projectDependency.getAttributes();
    }

    @Override
    public DependencyConstraint attributes(Action<? super AttributeContainer> configureAction) {
        projectDependency.attributes(configureAction);
        return this;
    }

    @Override
    public VersionConstraint getVersionConstraint() {
        return new DefaultImmutableVersionConstraint(
                "",
                projectDependency.getVersion(),
                "",
                Collections.emptyList(),
                ""
        );
    }

    @Override
    public String getGroup() {
        return projectDependency.getGroup();
    }

    @Override
    public String getName() {
        return projectDependency.getName();
    }

    @Nullable
    @Override
    public String getVersion() {
        return projectDependency.getVersion();
    }

    @Override
    public boolean matchesStrictly(ModuleVersionIdentifier identifier) {
        return identifier.getModule().equals(getModule()) && identifier.getVersion().equals(projectDependency.getVersion());
    }

    @Override
    public ModuleIdentifier getModule() {
        String group = projectDependency.getGroup();
        return DefaultModuleIdentifier.newId(group != null ? group : "", projectDependency.getName());
    }

    @Override
    public void setForce(boolean force) {
        this.force = force;
    }

    @Override
    public boolean isForce() {
        return force;
    }
}
