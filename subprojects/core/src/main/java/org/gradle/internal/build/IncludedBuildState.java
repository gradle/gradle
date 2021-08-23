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
import org.gradle.api.internal.GradleInternal;

import java.io.File;

/**
 * Encapsulates the identity and state of an included build. An included build is a nested build that participates in dependency resolution and task execution with the root build and other included builds in the build tree.
 */
public interface IncludedBuildState extends NestedBuildState, CompositeBuildParticipantBuildState {
    String getName();

    File getRootDirectory();

    boolean isPluginBuild();

    Action<? super DependencySubstitutions> getRegisteredDependencySubstitutions();

    GradleInternal getConfiguredBuild();

    <T> T withState(Transformer<T, ? super GradleInternal> action);
}
