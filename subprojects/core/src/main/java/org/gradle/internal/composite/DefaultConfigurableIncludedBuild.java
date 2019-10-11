/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.composite;

import com.google.common.base.Preconditions;
import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencySubstitutions;
import org.gradle.api.initialization.ConfigurableIncludedBuild;
import org.gradle.api.tasks.TaskReference;
import org.gradle.internal.ImmutableActionSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;

public class DefaultConfigurableIncludedBuild implements ConfigurableIncludedBuild {

    private final File projectDir;

    private String name;
    private ImmutableActionSet<DependencySubstitutions> dependencySubstitutionActions = ImmutableActionSet.empty();

    public DefaultConfigurableIncludedBuild(File projectDir) {
        this.projectDir = projectDir;
        this.name = projectDir.getName();
    }

    @Override
    @Nonnull
    public String getName() {
        return name;
    }

    @Override
    public void setName(@Nonnull String name) {
        Preconditions.checkNotNull(name, "name must not be null");
        this.name = name;
    }

    @Override
    @Nonnull
    public File getProjectDir() {
        return projectDir;
    }

    @Override
    public void dependencySubstitution(@Nonnull Action<? super DependencySubstitutions> action) {
        Preconditions.checkNotNull(action, "action must not be null");
        dependencySubstitutionActions = dependencySubstitutionActions.add(action);
    }

    @Override
    @Nonnull
    public TaskReference task(@Nullable String path) {
        throw new IllegalStateException("IncludedBuild.task() cannot be used while configuring the included build");
    }

    public Action<DependencySubstitutions> getDependencySubstitutionAction() {
        return dependencySubstitutionActions;
    }
}
