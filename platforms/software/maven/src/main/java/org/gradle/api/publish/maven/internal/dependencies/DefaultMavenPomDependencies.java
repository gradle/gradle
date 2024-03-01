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

package org.gradle.api.publish.maven.internal.dependencies;

import com.google.common.collect.ImmutableList;

/**
 * Default implementation of {@link MavenPomDependencies}.
 */
public class DefaultMavenPomDependencies implements MavenPomDependencies {

    private final ImmutableList<MavenDependency> dependencies;
    private final ImmutableList<MavenDependency> dependencyManagement;

    public DefaultMavenPomDependencies(
        ImmutableList<MavenDependency> dependencies,
        ImmutableList<MavenDependency> dependencyManagement
    ) {
        this.dependencies = dependencies;
        this.dependencyManagement = dependencyManagement;
    }

    @Override
    public ImmutableList<MavenDependency> getDependencies() {
        return dependencies;
    }

    @Override
    public ImmutableList<MavenDependency> getDependencyManagement() {
        return dependencyManagement;
    }
}
