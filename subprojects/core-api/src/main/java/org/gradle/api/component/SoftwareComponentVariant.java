/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.component;

import org.gradle.api.Named;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.capabilities.Capability;

import java.util.Set;

/**
 * A software component variant, which has a number of artifacts,
 * dependencies, constraints and capabilities, and that can be
 * published to various formats (Gradle metadata, POM, ivy.xml, ...)
 *
 * @since 5.3
 */
public interface SoftwareComponentVariant extends HasAttributes, Named {
    /**
     * Returns the artifacts.
     *
     * @since 5.3
     */
    Set<? extends PublishArtifact> getArtifacts();
    /**
     * Returns the dependencies.
     *
     * @since 5.3
     */
    Set<? extends ModuleDependency> getDependencies();
    /**
     * Returns the dependency constraints.
     *
     * @since 5.3
     */
    Set<? extends DependencyConstraint> getDependencyConstraints();
    /**
     * Returns the capabilities.
     *
     * @since 5.3
     */
    Set<? extends Capability> getCapabilities();
    /**
     * Returns the global excludes.
     *
     * @since 5.3
     */
    Set<ExcludeRule> getGlobalExcludes();
}
