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

import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal;

import java.util.Collections;
import java.util.List;

/**
 * Default implementation of {@link MavenPomDependencies}.
 */
public class DefaultMavenPomDependencies implements MavenPomDependencies {

    public static final DefaultMavenPomDependencies EMPTY = new DefaultMavenPomDependencies(
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList()
    );

    private final List<MavenDependencyInternal> runtimeDependencies;
    private final List<MavenDependencyInternal> apiDependencies;
    private final List<MavenDependencyInternal> optionalApiDependencies;
    private final List<MavenDependencyInternal> optionalRuntimeDependencies;
    private final List<MavenDependencyInternal> runtimeDependencyConstraints;
    private final List<MavenDependencyInternal> apiDependencyConstraints;
    private final List<MavenDependencyInternal> importDependencyConstraints;

    public DefaultMavenPomDependencies(
        List<MavenDependencyInternal> runtimeDependencies,
        List<MavenDependencyInternal> apiDependencies,
        List<MavenDependencyInternal> optionalApiDependencies,
        List<MavenDependencyInternal> optionalRuntimeDependencies,
        List<MavenDependencyInternal> runtimeDependencyConstraints,
        List<MavenDependencyInternal> apiDependencyConstraints,
        List<MavenDependencyInternal> importDependencyConstraints
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
    public List<MavenDependencyInternal> getRuntimeDependencies() {
        return runtimeDependencies;
    }

    @Override
    public List<MavenDependencyInternal> getApiDependencies() {
        return apiDependencies;
    }

    @Override
    public List<MavenDependencyInternal> getOptionalApiDependencies() {
        return optionalApiDependencies;
    }

    @Override
    public List<MavenDependencyInternal> getOptionalRuntimeDependencies() {
        return optionalRuntimeDependencies;
    }

    @Override
    public List<MavenDependencyInternal> getRuntimeDependencyManagement() {
        return runtimeDependencyConstraints;
    }

    @Override
    public List<MavenDependencyInternal> getApiDependencyManagement() {
        return apiDependencyConstraints;
    }

    @Override
    public List<MavenDependencyInternal> getImportDependencyManagement() {
        return importDependencyConstraints;
    }
}
