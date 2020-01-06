/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.plugins.ide.internal.resolver;

import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;

import java.io.File;
import java.util.Set;

/**
 * Used in conjunction with {@link IdeDependencySet} to adapt Gradle's dependency resolution API to the
 * specific needs of the IDE plugins.
 */
public interface IdeDependencyVisitor {
    /**
     * If true, external dependencies will be skipped.
     */
    boolean isOffline();

    /**
     * Should sources for external dependencies be downloaded?
     */
    boolean downloadSources();

    /**
     * Should javadoc for external dependencies be downloaded?
     */
    boolean downloadJavaDoc();

    /**
     * The dependency points to an artifact built by another project.
     * The component identifier is guaranteed to be a {@link org.gradle.api.artifacts.component.ProjectComponentIdentifier}.
     */
    void visitProjectDependency(ResolvedArtifactResult artifact);

    /**
     * The dependency points to an external module.
     * The component identifier is guaranteed to be a {@link org.gradle.api.artifacts.component.ModuleComponentIdentifier}.
     * The source and javadoc locations maybe be empty, but never null.
     */
    void visitModuleDependency(ResolvedArtifactResult artifact, Set<ResolvedArtifactResult> sources, Set<ResolvedArtifactResult> javaDoc, boolean testDependency);

    /**
     * The dependency points neither to a project, nor an external module, so this method should treat it as an opaque file.
     */
    void visitFileDependency(ResolvedArtifactResult artifact, boolean testDependency);

    /**
     * A generated file dependency to which we might be able to attach sources
     */
    void visitGradleApiDependency(ResolvedArtifactResult artifact, File sources, boolean testDependency);

    /**
     * There was an unresolved dependency in the result.
     */
    void visitUnresolvedDependency(UnresolvedDependencyResult unresolvedDependency);
}
