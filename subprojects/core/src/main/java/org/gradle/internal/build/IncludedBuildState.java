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

package org.gradle.internal.build;

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.DependencySubstitutions;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.internal.Pair;

import java.io.File;
import java.util.Set;

/**
 * Encapsulates the identity and state of an included build. An included build is a nested build that participates in dependency resolution and task execution with the root build and other included builds in the build tree.
 */
public interface IncludedBuildState extends NestedBuildState {
    String getName();
    File getRootDirectory();
    IncludedBuild getModel();
    Action<? super DependencySubstitutions> getRegisteredDependencySubstitutions();
    Set<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> getAvailableModules();

    /**
     * Creates a copy of the identifier for a project in this build, to use in the dependency resolution result from some other build
     */
    ProjectComponentIdentifier idToReferenceProjectFromAnotherBuild(ProjectComponentIdentifier identifier);

    SettingsInternal loadSettings();

    GradleInternal getConfiguredBuild();
    void finishBuild();
    void addTasks(Iterable<String> tasks);
    void execute(Iterable<String> tasks, Object listener);

    <T> T withState(Transformer<T, ? super GradleInternal> action);
}
