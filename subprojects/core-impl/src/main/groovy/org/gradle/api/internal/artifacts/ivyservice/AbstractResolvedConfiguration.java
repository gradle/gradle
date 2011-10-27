/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.*;
import org.gradle.api.internal.CachingDirectedGraphWalker;
import org.gradle.api.internal.DirectedGraphWithEdgeValues;
import org.gradle.api.specs.Spec;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

abstract class AbstractResolvedConfiguration implements ResolvedConfiguration {
    private final CachingDirectedGraphWalker<ResolvedDependency, ResolvedArtifact> walker
            = new CachingDirectedGraphWalker<ResolvedDependency, ResolvedArtifact>(new ResolvedDependencyArtifactsGraph());

    protected abstract ResolvedDependency getRoot();

    abstract Set<ResolvedDependency> doGetFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec);

    abstract Set<UnresolvedDependency> getUnresolvedDependencies();

    public LenientConfiguration getLenientConfiguration() {
        return new LenientConfigurationImpl(this);
    }

    public Set<ResolvedDependency> getFirstLevelModuleDependencies() {
        rethrowFailure();
        return getRoot().getChildren();
    }

    public Set<File> getFiles(Spec<? super Dependency> dependencySpec) {
        rethrowFailure();
        return doGetFiles(dependencySpec);
    }

    public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) throws ResolveException {
        rethrowFailure();
        return doGetFirstLevelModuleDependencies(dependencySpec);
    }

    Set<File> doGetFiles(Spec<? super Dependency> dependencySpec) {
        Set<ResolvedDependency> firstLevelModuleDependencies = doGetFirstLevelModuleDependencies(dependencySpec);

        Set<ResolvedArtifact> artifacts = new LinkedHashSet<ResolvedArtifact>();

        for (ResolvedDependency resolvedDependency : firstLevelModuleDependencies) {
            artifacts.addAll(resolvedDependency.getParentArtifacts(getRoot()));
            walker.add(resolvedDependency);
        }

        artifacts.addAll(walker.findValues());

        Set<File> files = new LinkedHashSet<File>();
        for (ResolvedArtifact artifact : artifacts) {
            File depFile = artifact.getFile();
            if (depFile != null) {
                files.add(depFile);
            }
        }
        return files;
    }

    private static class ResolvedDependencyArtifactsGraph implements DirectedGraphWithEdgeValues<ResolvedDependency, ResolvedArtifact> {
        public void getNodeValues(ResolvedDependency node, Collection<ResolvedArtifact> values,
                                  Collection<ResolvedDependency> connectedNodes) {
            connectedNodes.addAll(node.getChildren());
        }

        public void getEdgeValues(ResolvedDependency from, ResolvedDependency to,
                                  Collection<ResolvedArtifact> values) {
            values.addAll(to.getParentArtifacts(from));
        }
    }
}
