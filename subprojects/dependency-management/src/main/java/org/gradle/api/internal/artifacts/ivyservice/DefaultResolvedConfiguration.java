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

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;

import java.io.File;
import java.util.Collection;
import java.util.Set;

public class DefaultResolvedConfiguration implements ResolvedConfiguration {
    private final DefaultLenientConfiguration configuration;

    public DefaultResolvedConfiguration(DefaultLenientConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public boolean hasError() {
        return configuration.hasError();
    }

    @Override
    public void rethrowFailure() throws ResolveException {
        configuration.rethrowFailure();
    }

    @Override
    public LenientConfiguration getLenientConfiguration() {
        return configuration;
    }

    @Override
    public Set<File> getFiles() throws ResolveException {
        return getFiles(Specs.<Dependency>satisfyAll());
    }

    @Override
    public Set<File> getFiles(final Spec<? super Dependency> dependencySpec) throws ResolveException {
        ResolvedFilesCollectingVisitor visitor = new ResolvedFilesCollectingVisitor();
        configuration.select(dependencySpec).visitArtifacts(visitor, false);
        Collection<Throwable> failures = visitor.getFailures();
        if (!failures.isEmpty()) {
            throw new DefaultLenientConfiguration.ArtifactResolveException("files", configuration.getConfiguration().getIdentityPath().toString(), configuration.getConfiguration().getDisplayName(), failures);
        }
        return visitor.getFiles();
    }

    @Override
    public Set<ResolvedDependency> getFirstLevelModuleDependencies() throws ResolveException {
        rethrowFailure();
        return configuration.getFirstLevelModuleDependencies();
    }

    @Override
    public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) throws ResolveException {
        rethrowFailure();
        return configuration.getFirstLevelModuleDependencies(dependencySpec);
    }

    @Override
    public Set<ResolvedArtifact> getResolvedArtifacts() throws ResolveException {
        rethrowFailure();
        ArtifactCollectingVisitor visitor = new ArtifactCollectingVisitor();
        configuration.select().visitArtifacts(visitor, false);
        return visitor.getArtifacts();
    }
}
