/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultResolvedArtifactResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.LocalConfigurationMetaData;
import org.gradle.internal.component.model.ConfigurationMetaData;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.jvm.JarBinarySpec;
import org.gradle.language.base.DependentSourceSet;
import org.gradle.language.base.internal.model.DefaultVariantsMetaData;
import org.gradle.language.base.internal.model.VariantsMetaData;
import org.gradle.language.base.internal.resolve.DependentSourceSetResolveContext;
import org.gradle.language.base.internal.resolve.LibraryResolveException;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.platform.base.DependencySpec;

import java.io.File;
import java.util.*;

import static com.google.common.collect.Iterables.concat;
import static org.gradle.util.CollectionUtils.collect;

public class DependencyResolvingClasspath extends AbstractFileCollection {
    private final GlobalDependencyResolutionRules globalRules = GlobalDependencyResolutionRules.NO_OP;
    private final List<ResolutionAwareRepository> remoteRepositories;
    private final JarBinarySpecInternal binary;
    private final DependentSourceSet sourceSet;
    private final ArtifactDependencyResolver dependencyResolver;
    private final DependentSourceSetResolveContext resolveContext;

    private ResolveResult resolveResult;

    public DependencyResolvingClasspath(
            JarBinarySpecInternal binarySpec,
            DependentSourceSet sourceSet,
            ArtifactDependencyResolver dependencyResolver,
            ModelSchemaStore schemaStore,
            List<ResolutionAwareRepository> remoteRepositories) {
        this.binary = binarySpec;
        this.sourceSet = sourceSet;
        this.dependencyResolver = dependencyResolver;
        this.remoteRepositories = remoteRepositories;
        this.resolveContext = new DependentSourceSetResolveContext(binary.getId(), sourceSet, variantsMetaDataFrom(binary, schemaStore), allDependencies());
    }

    @Override
    public String getDisplayName() {
        return "Classpath for " + sourceSet.getDisplayName();
    }

    @Override
    public Set<File> getFiles() {
        ensureResolved(true);
        Set<ResolvedArtifact> artifacts = resolveResult.artifactResults.getArtifacts();
        return collect(artifacts, new org.gradle.api.Transformer<File, ResolvedArtifact>() {
            @Override
            public File transform(ResolvedArtifact resolvedArtifact) {
                return resolvedArtifact.getFile();
            }
        });
    }

    @Override
    public TaskDependency getBuildDependencies() {
        ensureResolved(false);
        return resolveResult.taskDependency;
    }

    private Iterable<DependencySpec> allDependencies() {
        return new Iterable<DependencySpec>() {
            @Override
            public Iterator<DependencySpec> iterator() {
                return concat(sourceSetDependencies(), componentDependencies(), apiDependencies()).iterator();
            }
        };
    }

    private Collection<DependencySpec> componentDependencies() {
        return binary.getDependencies();
    }

    private Collection<DependencySpec> sourceSetDependencies() {
        return sourceSet.getDependencies().getDependencies();
    }

    private Collection<DependencySpec> apiDependencies() {
        return binary.getApiDependencies();
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
        dependencyResolver.resolve(resolveContext, remoteRepositories, globalRules, result, result);
        return result;
    }

    private void failOnUnresolvedDependency(List<Throwable> notFound) {
        if (!notFound.isEmpty()) {
            throw new LibraryResolveException(String.format("Could not resolve all dependencies for '%s' source set '%s'", binary.getDisplayName(), sourceSet.getDisplayName()), notFound);
        }
    }

    private VariantsMetaData variantsMetaDataFrom(JarBinarySpec binary, ModelSchemaStore schemaStore) {
        return DefaultVariantsMetaData.extractFrom(binary, schemaStore);
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
            ConfigurationMetaData configurationMetaData = resolvedConfiguration.getMetaData();
            if (configurationMetaData instanceof LocalConfigurationMetaData) {
                TaskDependency directBuildDependencies = ((LocalConfigurationMetaData) configurationMetaData).getDirectBuildDependencies();
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
        public void visitArtifacts(ResolvedConfigurationIdentifier parent, ResolvedConfigurationIdentifier child, ArtifactSet artifacts) {
            artifactResults.addArtifactSet(artifacts);
        }

        @Override
        public void finishArtifacts() {
            artifactResults.resolveNow();
        }
    }
}
