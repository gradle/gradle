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

package org.gradle.api.publish.maven.internal.publication;

import com.google.common.collect.ImmutableList;
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal;

/**
 * Default implementation of {@link MavenPomDependencies}.
 */
public class DefaultMavenPomDependencies implements MavenPomDependencies {

    public static final DefaultMavenPomDependencies EMPTY = new DefaultMavenPomDependencies(
        ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of(),
        ImmutableList.of(), ImmutableList.of(), ImmutableList.of()
    );

    private final ImmutableList<MavenDependencyInternal> runtimeDependencies;
    private final ImmutableList<MavenDependencyInternal> apiDependencies;
    private final ImmutableList<MavenDependencyInternal> optionalApiDependencies;
    private final ImmutableList<MavenDependencyInternal> optionalRuntimeDependencies;
    private final ImmutableList<MavenDependencyInternal> runtimeDependencyConstraints;
    private final ImmutableList<MavenDependencyInternal> apiDependencyConstraints;
    private final ImmutableList<MavenDependencyInternal> importDependencyConstraints;

    public DefaultMavenPomDependencies(
        ImmutableList<MavenDependencyInternal> runtimeDependencies,
        ImmutableList<MavenDependencyInternal> apiDependencies,
        ImmutableList<MavenDependencyInternal> optionalApiDependencies,
        ImmutableList<MavenDependencyInternal> optionalRuntimeDependencies,
        ImmutableList<MavenDependencyInternal> runtimeDependencyConstraints,
        ImmutableList<MavenDependencyInternal> apiDependencyConstraints,
        ImmutableList<MavenDependencyInternal> importDependencyConstraints
    ) {
        this.runtimeDependencies = runtimeDependencies;
        this.apiDependencies = apiDependencies;
        this.optionalApiDependencies = optionalApiDependencies;
        this.optionalRuntimeDependencies = optionalRuntimeDependencies;
        this.runtimeDependencyConstraints = runtimeDependencyConstraints;
        this.apiDependencyConstraints = apiDependencyConstraints;
        this.importDependencyConstraints = importDependencyConstraints;
    }

    @Override
    public ImmutableList<MavenDependencyInternal> getRuntimeDependencies() {
        return runtimeDependencies;
    }

    @Override
    public ImmutableList<MavenDependencyInternal> getApiDependencies() {
        return apiDependencies;
    }

    @Override
    public ImmutableList<MavenDependencyInternal> getOptionalApiDependencies() {
        return optionalApiDependencies;
    }

    @Override
    public ImmutableList<MavenDependencyInternal> getOptionalRuntimeDependencies() {
        return optionalRuntimeDependencies;
    }

    @Override
    public ImmutableList<MavenDependencyInternal> getRuntimeDependencyManagement() {
        return runtimeDependencyConstraints;
    }

    @Override
    public ImmutableList<MavenDependencyInternal> getApiDependencyManagement() {
        return apiDependencyConstraints;
    }

    @Override
    public ImmutableList<MavenDependencyInternal> getImportDependencyManagement() {
        return importDependencyConstraints;
    }
}
