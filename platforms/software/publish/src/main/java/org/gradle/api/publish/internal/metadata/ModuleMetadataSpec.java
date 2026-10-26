/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.publish.internal.metadata;

import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * A complete description of a GMM file that can be published without additional context.
 */
public class ModuleMetadataSpec {

    final Identity identity;
    final List<Variant> variants;
    final boolean mustIncludeBuildId;

    ModuleMetadataSpec(
        Identity identity,
        List<Variant> variants,
        boolean mustIncludeBuildId
    ) {
        this.identity = identity;
        this.variants = variants;
        this.mustIncludeBuildId = mustIncludeBuildId;
    }

    record Identity(
        ModuleVersionIdentifier coordinates,
        List<Attribute> attributes,
        @Nullable String relativeUrl
    ) { }

    record LocalVariant(
        String name,
        List<Attribute> attributes,
        List<Capability> capabilities,
        Set<Dependency> dependencies,
        Set<DependencyConstraint> dependencyConstraints,
        List<Artifact> artifacts
    ) implements Variant { }

    record RemoteVariant(
        String name,
        List<Attribute> attributes,
        AvailableAt availableAt,
        List<Capability> capabilities
    ) implements Variant { }

    record Dependency(
        DependencyCoordinates coordinates,
        Set<ExcludeRule> excludeRules,
        List<Attribute> attributes,
        List<Capability> requestedCapabilities,
        boolean endorseStrictVersions,
        String reason,
        ArtifactSelector artifactSelector
    ) { }

    sealed interface Variant permits LocalVariant, RemoteVariant {
    }

    record Attribute(
        String name,
        Object value
    ) { }

    record Capability(
        String group,
        String name,
        @Nullable String version
    ) { }

    record Version(
        @Nullable String requires,
        @Nullable String strictly,
        @Nullable String preferred,
        List<String> rejectedVersions
    ) { }

    record DependencyCoordinates(
        String group,
        String name,
        Version version
    ) { }

    record ArtifactSelector(
        String name,
        String type,
        @Nullable String extension,
        @Nullable String classifier
    ) { }

    record DependencyConstraint(
        DependencyCoordinates coordinates,
        List<Attribute> attributes,
        String reason
    ) { }

    record Artifact(
        String name,
        String uri,
        File file
    ) { }

    record AvailableAt(
        String url,
        ModuleVersionIdentifier coordinates
    ) { }

}
