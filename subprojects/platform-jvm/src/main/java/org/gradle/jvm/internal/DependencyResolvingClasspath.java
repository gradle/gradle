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

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.DefaultResolverResults;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.jvm.JarBinarySpec;
import org.gradle.language.base.internal.DependentSourceSetInternal;
import org.gradle.language.base.internal.model.DefaultVariantsMetaData;
import org.gradle.language.base.internal.resolve.DependentSourceSetResolveContext;
import org.gradle.language.base.internal.resolve.LibraryResolveException;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class DependencyResolvingClasspath extends AbstractFileCollection {
    private final GlobalDependencyResolutionRules globalRules = GlobalDependencyResolutionRules.NO_OP;
    private final List<ResolutionAwareRepository> remoteRepositories = Lists.newArrayList();
    private final JarBinarySpec binary;
    private final DependentSourceSetInternal sourceSet;
    private final ArtifactDependencyResolver dependencyResolver;
    private final DependentSourceSetResolveContext resolveContext;

    private ResolverResults cachedResolveResults;
    private TaskDependency taskDependency;

    public DependencyResolvingClasspath(
            JarBinarySpec binarySpec,
            DependentSourceSetInternal sourceSet,
            ArtifactDependencyResolver dependencyResolver) {
        this.binary = binarySpec;
        this.sourceSet = sourceSet;
        this.dependencyResolver = dependencyResolver;
        resolveContext = new DependentSourceSetResolveContext(binary.getId(), sourceSet, DefaultVariantsMetaData.extractFrom(binary));
    }

    @Override
    public String getDisplayName() {
        return "Classpath for " + sourceSet.getDisplayName();
    }

    @Override
    public Set<File> getFiles() {
        ensureResolved();
        Set<ResolvedArtifact> artifacts = cachedResolveResults.getResolvedArtifacts().getArtifacts();
        return CollectionUtils.collect(artifacts, new org.gradle.api.Transformer<File, ResolvedArtifact>() {
            @Override
            public File transform(ResolvedArtifact resolvedArtifact) {
                return resolvedArtifact.getFile();
            }
        });
    }

    @Override
    public TaskDependency getBuildDependencies() {
        ensureResolved();
        return taskDependency;
    }

    private void ensureResolved() {
        if (cachedResolveResults == null) {
            DefaultResolverResults results = new DefaultResolverResults();

            dependencyResolver.resolve(resolveContext, remoteRepositories, globalRules, results);
            failOnUnresolvedDependency(results.getResolutionResult());

            taskDependency = results.getResolvedLocalComponents().getComponentBuildDependencies();
            cachedResolveResults = results;
        }
    }

    private void failOnUnresolvedDependency(ResolutionResult resolutionResult) {
        final List<Throwable> notFound = new LinkedList<Throwable>();
        resolutionResult.allDependencies(new Action<DependencyResult>() {
            @Override
            public void execute(DependencyResult dependencyResult) {
                if (dependencyResult instanceof UnresolvedDependencyResult) {
                    UnresolvedDependencyResult unresolved = (UnresolvedDependencyResult) dependencyResult;
                    notFound.add(unresolved.getFailure());
                }
            }
        });
        if (!notFound.isEmpty()) {
            throw new LibraryResolveException(String.format("Could not resolve all dependencies for '%s' source set '%s'", binary.getDisplayName(), sourceSet.getDisplayName()), notFound);
        }
    }
}
