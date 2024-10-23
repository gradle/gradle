/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.component.external.model.maven;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.internal.component.external.descriptor.MavenScope;
import org.gradle.internal.component.external.model.ExternalDependencyDescriptor;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Represents a dependency as represented in a Maven POM file.
 */
public class MavenDependencyDescriptor extends ExternalDependencyDescriptor {
    private final ModuleComponentSelector selector;
    private final MavenScope scope;
    private final MavenDependencyType type;
    private final ImmutableList<ExcludeMetadata> excludes;

    // A dependency artifact will be defined if the descriptor specified a classifier or non-default type attribute.
    @Nullable
    private final IvyArtifactName dependencyArtifact;

    public MavenDependencyDescriptor(MavenScope scope, MavenDependencyType type, ModuleComponentSelector selector,
                                     @Nullable IvyArtifactName dependencyArtifact, List<ExcludeMetadata> excludes) {
        this.scope = scope;
        this.selector = selector;
        this.type = type;
        this.dependencyArtifact = dependencyArtifact;
        this.excludes = ImmutableList.copyOf(excludes);
    }

    @Override
    public String toString() {
        return "dependency: " + getSelector() + ", scope: " + scope + ", optional: " + isOptional();
    }

    public MavenScope getScope() {
        return scope;
    }

    @Override
    public boolean isChanging() {
        return false;
    }

    @Override
    public boolean isTransitive() {
        return !(isConstraint() || isOptional());
    }

    @Override
    protected MavenDependencyDescriptor withRequested(ModuleComponentSelector newRequested) {
        return new MavenDependencyDescriptor(scope, type, newRequested, dependencyArtifact, excludes);
    }

    public List<ExcludeMetadata> getAllExcludes() {
        return excludes;
    }

    public MavenDependencyType getType() {
        return type;
    }

    public List<ExcludeMetadata> getConfigurationExcludes() {
        // Ignore exclusions for dependencies with `<optional>true</optional>`, but not for <dependencyManagement>.
        if (type == MavenDependencyType.OPTIONAL_DEPENDENCY) {
            return Collections.emptyList();
        }
        return excludes;
    }

    /**
     * A Maven dependency has a 'dependency artifact' when it specifies a classifier or type attribute.
     */
    @Nullable
    public IvyArtifactName getDependencyArtifact() {
        return dependencyArtifact;
    }

    /**
     * When a Maven dependency declares a classifier or type attribute, this is modelled as a 'dependency artifact'.
     * This means that instead of resolving the default artifacts for the target dependency, we'll use the one defined
     * for the dependency.
     */
    public ImmutableList<IvyArtifactName> getConfigurationArtifacts() {
        // Special handling for artifacts declared for optional dependencies
        if (isOptional()) {
            return getArtifactsForOptionalDependency();
        }
        return getDependencyArtifacts();
    }

    /**
     * When an optional dependency declares a classifier, that classifier is effectively ignored, and the optional
     * dependency will update the version of any dependency with matching GAV.
     * (Same goes for {@code <type>} on optional dependencies: they are effectively ignored).
     *
     * Note that this doesn't really match with Maven, where an optional dependency with classifier will
     * provide a version for any other dependency with matching GAV + classifier.
     */
    private ImmutableList<IvyArtifactName> getArtifactsForOptionalDependency() {
        return ImmutableList.of();
    }

    /**
     * For a Maven dependency, the artifacts list as zero or one Artifact, always with '*' configuration
     */
    private ImmutableList<IvyArtifactName> getDependencyArtifacts() {
        return dependencyArtifact == null ? ImmutableList.of() : ImmutableList.of(dependencyArtifact);
    }

    @Override
    public ModuleComponentSelector getSelector() {
        return selector;
    }

    @Override
    public boolean isOptional() {
        return type == MavenDependencyType.OPTIONAL_DEPENDENCY;
    }

    @Override
    public boolean isConstraint() {
        return type == MavenDependencyType.DEPENDENCY_MANAGEMENT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MavenDependencyDescriptor that = (MavenDependencyDescriptor) o;
        return type == that.type
            && Objects.equal(selector, that.selector)
            && scope == that.scope
            && Objects.equal(excludes, that.excludes)
            && Objects.equal(dependencyArtifact, that.dependencyArtifact);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(
            selector,
            scope,
            type,
            excludes,
            dependencyArtifact);
    }
}
