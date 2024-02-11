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

package org.gradle.internal.component.model;

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata;

import java.util.Optional;

/**
 * Meta-data for an artifact that belongs to some component.
 */
public interface ComponentArtifactMetadata {
    /**
     * Returns the identifier for this artifact.
     */
    ComponentArtifactIdentifier getId();

    /**
     * Returns the identifier for the component that this artifact belongs to.
     */
    ComponentIdentifier getComponentId();

    /**
     * Returns this artifact as an Ivy artifact. This method is here to allow the artifact to be exposed in a backward-compatible way.
     */
    IvyArtifactName getName();

    /**
     * Collects the build dependencies of this artifact, which are required to build this artifact
     */
    TaskDependency getBuildDependencies();

    /**
     * Allows metadata with non-standard packaging to add a "fallback" artifact, to be resolved only when resolution fails.
     *
     * Typical use-cases are:
     * <ol>
     *     <li>Maven POM declares {@code pom} packaging, but actually the artifact is a {@code jar}.</li>
     *     <li>Maven POM declares an atypical packaging which does not match the artifact's type/extension property.  See <a href="https://repo1.maven.org/maven2/org/glassfish/ha/ha-api/3.1.7/">hk2-jar example</a>.
     * </ol>
     *
     * In these cases, supplying the alternative artifact metadata is a way to allow a re-fetch a different artifact file for the same component.
     *
     * <p>Defaults to {@link Optional#empty()}
     *
     * @return an optional artifact metadata, which if present will be resolved if this artifact's resolution fails
     * @see DefaultModuleComponentArtifactMetadata#DefaultModuleComponentArtifactMetadata(org.gradle.api.artifacts.component.ModuleComponentIdentifier, org.gradle.internal.component.model.IvyArtifactName, org.gradle.internal.component.model.ComponentArtifactMetadata)
     */
    default Optional<ComponentArtifactMetadata> getAlternativeArtifact() {
        return Optional.empty();
    }

    default boolean isOptionalArtifact() {
        return false;
    }
}
