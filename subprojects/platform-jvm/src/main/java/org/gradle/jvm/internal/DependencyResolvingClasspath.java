/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.jvm.internal;

import org.gradle.api.AttributesSchema;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultResolvedArtifactResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.language.base.internal.resolve.LibraryResolveException;
import org.gradle.platform.base.internal.BinarySpecInternal;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class DependencyResolvingClasspath extends AbstractFileCollection {
    private final GlobalDependencyResolutionRules globalRules = GlobalDependencyResolutionRules.NO_OP;
    private final List<ResolutionAwareRepository> remoteRepositories;
    private final BinarySpecInternal binary;
    private final ArtifactDependencyResolver dependencyResolver;
    private final ResolveContext resolveContext;
    private final AttributesSchema attributesSchema;

    private final String descriptor;
    private ResolveResult resolveResult;

    public DependencyResolvingClasspath(
        BinarySpecInternal binarySpec,
        String descriptor,
        ArtifactDependencyResolver dependencyResolver,
        List<ResolutionAwareRepository> remoteRepositories,
        ResolveContext resolveContext,
        AttributesSchema attributesSchema) {
        this.binary = binarySpec;
        this.descriptor = descriptor;
        this.dependencyResolver = dependencyResolver;
        this.remoteRepositories = remoteRepositories;
        this.resolveContext = resolveContext;
        this.attributesSchema = attributesSchema;
    }

    @Override
    public String getDisplayName() {
        return "Classpath for " + descriptor;
    }

    @Override
    public Set<File> getFiles() {
        ensureResolved(true);
        final Set<File> result = new LinkedHashSet<File>();
        resolveResult.artifactResults.getArtifacts().visit(new ArtifactVisitor() {
            @Override
            public void visitArtifact(ResolvedArtifact artifact) {
                result.add(artifact.getFile());
            }

            @Override
            public boolean includeFiles() {
                return true;
            }

            @Override
            public void visitFiles(@Nullable ComponentIdentifier componentIdentifier, Iterable<File> files) {
                for (File file : files) {
                    result.add(file);
                }
            }
        });
        return result;
    }

    @Override
    public TaskDependency getBuildDependencies() {
        ensureResolved(false);
        return resolveResult.taskDependency;
    }

    private void ensureResolved(boolean failFast) {
        if (resolveResult == null) {
            resolveResult = resolve();
        }
        if (failFast) {
            failOnUnresolvedDependency(resolveResult.notFound);
        }
    }

    private ResolveResult resolve() {
        ResolveResult result = new ResolveResult();
        dependencyResolver.resolve(resolveContext, remoteRepositories, globalRules, Specs.<DependencyMetadata>satisfyAll(), result, result, attributesSchema);
        return result;
    }

    private void failOnUnresolvedDependency(List<Throwable> notFound) {
        if (!notFound.isEmpty()) {
            throw new LibraryResolveException(String.format("Could not resolve all dependencies for '%s' %s", binary.getDisplayName(), descriptor), notFound);
        }
    }

    class ResolveResult implements DependencyGraphVisitor, DependencyArtifactsVisitor {
        public final DefaultTaskDependency taskDependency = new DefaultTaskDependency();
        public final List<Throwable> notFound = new LinkedList<Throwable>();
        public final DefaultResolvedArtifactResults artifactResults = new DefaultResolvedArtifactResults();

        @Override
        public void start(DependencyGraphNode root) {
        }

        @Override
        public void visitNode(DependencyGraphNode resolvedConfiguration) {
            ConfigurationMetadata configurationMetadata = resolvedConfiguration.getMetadata();
            if (configurationMetadata instanceof LocalConfigurationMetadata) {
                TaskDependency directBuildDependencies = ((LocalConfigurationMetadata) configurationMetadata).getArtifactBuildDependencies();
                taskDependency.add(directBuildDependencies);
            }

            for (DependencyGraphEdge dependency : resolvedConfiguration.getOutgoingEdges()) {
                ModuleVersionResolveException failure = dependency.getFailure();
                if (failure != null) {
                    notFound.add(failure);
                }
            }
        }

        @Override
        public void visitEdge(DependencyGraphNode resolvedConfiguration) {
        }

        @Override
        public void finish(DependencyGraphNode root) {
        }

        @Override
        public void visitArtifacts(DependencyGraphNode parent, DependencyGraphNode child, ArtifactSet artifacts) {
            artifactResults.addArtifactSet(artifacts);
        }

        @Override
        public void finishArtifacts() {
            artifactResults.resolveNow();
        }
    }
}
