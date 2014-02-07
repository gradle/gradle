/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugins.ide.internal.resolver.translator;

import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class AbstractExternalModuleDependencyTranslator implements ExternalModuleDependencyTranslator {
    public List<ExternalDependency> translate(Set<ResolvedDependency> resolvedDependencies) {
        List<ExternalDependency> externalDependencies = new ArrayList<ExternalDependency>();

        for(ResolvedDependency resolvedDependency : resolvedDependencies) {
            ExternalModuleDependency dependency = new DefaultExternalModuleDependency(resolvedDependency.getModuleGroup(), resolvedDependency.getModuleName(), resolvedDependency.getModuleVersion(), resolvedDependency.getConfiguration());
            dependency.setTransitive(false);
            addArtifact(dependency);
            externalDependencies.add(dependency);
        }

        return externalDependencies;
    }

    private void addArtifact(ExternalModuleDependency dependency) {
        DependencyArtifact artifact = createArtifact(dependency.getName());
        dependency.addArtifact(artifact);
    }

    abstract DependencyArtifact createArtifact(String dependencyName);
}