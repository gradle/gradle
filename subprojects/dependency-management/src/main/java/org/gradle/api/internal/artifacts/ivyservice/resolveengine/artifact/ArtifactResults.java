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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.specs.Spec;

import java.io.File;
import java.util.Collection;

public interface ArtifactResults {
    /**
     * Collects files reachable from first level dependencies that satisfy the given spec. Fails when any file cannot be resolved.
     */
    void collectFiles(Spec<? super Dependency> dependencySpec, Collection<File> dest) throws ResolveException;

    /**
     * Collects all artifacts into the given collection. Fails when any artifact cannot be resolved.
     *
     * @throws ResolveException On failure to resolve or download any artifact.
     */
    void collectArtifacts(Collection<? super ResolvedArtifactResult> dest) throws ResolveException;
}
