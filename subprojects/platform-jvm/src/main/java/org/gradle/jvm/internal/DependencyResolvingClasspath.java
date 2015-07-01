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

import org.gradle.api.Action;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.*;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResult;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.language.base.internal.DependentSourceSetInternal;
import org.gradle.language.base.internal.resolve.DependentSourceSetResolveContext;
import org.gradle.language.base.internal.resolve.LibraryResolveException;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.Platform;

import java.io.File;
import java.util.*;

public class DependencyResolvingClasspath extends AbstractFileCollection {
    private final String projectPath;
    private final String componentName;
    private final DependentSourceSetInternal sourceSet;
    private final BinarySpec binary;
    private final ArtifactDependencyResolver dependencyResolver;
    private final FileCollection compileClasspath;
    private final Platform platform;

    private ResolverResults resolverResults;
    private TaskDependency taskDependency;

    public DependencyResolvingClasspath(
        String projectPath,
        String componentName,
        BinarySpec binarySpec,
        DependentSourceSetInternal sourceSet,
        ArtifactDependencyResolver dependencyResolver,
        FileCollection compileClasspath, Platform platform) {
        this.projectPath = projectPath;
        this.componentName = componentName;
        this.binary = binarySpec;
        this.sourceSet = sourceSet;
        this.dependencyResolver = dependencyResolver;
        this.compileClasspath = compileClasspath;
        this.platform = platform;
    }

    @Override
    public String getDisplayName() {
        return "Classpath for " + sourceSet.getDisplayName();
    }

    @Override
    public Set<File> getFiles() {
        ensureResolved();
        Set<File> classpath = new LinkedHashSet<File>();
        classpath.addAll(compileClasspath.getFiles());
        Set<ResolvedArtifact> artifacts = resolverResults.getResolvedArtifacts().getArtifacts();
        for (ResolvedArtifact resolvedArtifact : artifacts) {
            classpath.add(resolvedArtifact.getFile());
        }
        return classpath;
    }

    private DependentSourceSetResolveContext createResolveContext() {
        return new DependentSourceSetResolveContext(projectPath, componentName, binary.getName(), sourceSet, platform);
    }

    @Override
    public TaskDependency getBuildDependencies() {
        ensureResolved();
        return taskDependency;
    }

    private void ensureResolved() {
        if (resolverResults==null) {
            final DefaultTaskDependency result = new DefaultTaskDependency();
            result.add(super.getBuildDependencies());
            final List<Throwable> notFound = new LinkedList<Throwable>();
            resolve(createResolveContext(), new Action<DefaultResolverResults>() {
                @Override
                public void execute(DefaultResolverResults resolverResults) {
                    if (!resolverResults.hasError()) {
                        ResolvedLocalComponentsResult resolvedLocalComponents = resolverResults.getResolvedLocalComponents();
                        result.add(resolvedLocalComponents.getComponentBuildDependencies());
                    }
                    resolverResults.getResolutionResult().allDependencies(new Action<DependencyResult>() {
                        @Override
                        public void execute(DependencyResult dependencyResult) {
                            if (dependencyResult instanceof UnresolvedDependencyResult) {
                                UnresolvedDependencyResult unresolved = (UnresolvedDependencyResult) dependencyResult;
                                notFound.add(unresolved.getFailure());
                            }
                        }
                    });
                }
            });
            if (!notFound.isEmpty()) {
                throw new LibraryResolveException(String.format("Could not resolve all dependencies for '%s' source set '%s'", binary.getDisplayName(), sourceSet.getDisplayName()), notFound);
            }
            taskDependency = result;
        }
    }

    public void resolve(ResolveContext resolveContext, Action<DefaultResolverResults> onResolve) {
        resolverResults = new DefaultResolverResults();
        dependencyResolver.resolve(resolveContext, Collections.<ResolutionAwareRepository>emptyList(), GlobalDependencyResolutionRules.NO_OP, (DefaultResolverResults) resolverResults);
        onResolve.execute((DefaultResolverResults) resolverResults);
    }
}
