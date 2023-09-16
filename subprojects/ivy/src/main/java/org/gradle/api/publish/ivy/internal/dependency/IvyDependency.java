/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.dependency;

import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExcludeRule;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Represents a dependency within an ivy descriptor file. This is the "Ivy view"
 * of the dependency, after being converted from Gradle's dependency model.
 */
public interface IvyDependency {
    /**
     * The organisation value for this dependency.
     */
    String getOrganisation();

    /**
     * The module value for this dependency.
     */
    String getModule();

    /**
     * The revision value for this dependency.
     */
    String getRevision();

    /**
     * The configuration mapping for this dependency. This is mapped to the
     * {@code conf} field on the Ivy dependency element.
     */
    String getConfMapping();

    /**
     * If this dependency is marked transitive.
     */
    boolean isTransitive();

    /**
     * The dynamic revision constraint originally used for this dependency.
     */
    @Nullable
    String getRevConstraint();

    /**
     * The requested artifacts for this dependency.
     */
    Set<DependencyArtifact> getArtifacts();

    /**
     * The exclude rules for this dependency.
     */
    Set<ExcludeRule> getExcludeRules();
}
