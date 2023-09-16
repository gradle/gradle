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

import javax.annotation.Nullable;
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

    static class Identity {

        final ModuleVersionIdentifier coordinates;
        final List<Attribute> attributes;
        @Nullable
        final String relativeUrl;

        Identity(
            ModuleVersionIdentifier coordinates,
            List<Attribute> attributes,
            @Nullable String relativeUrl
        ) {
            this.coordinates = coordinates;
            this.attributes = attributes;
            this.relativeUrl = relativeUrl;
        }
    }

    static class LocalVariant extends Variant {

        final String name;
        final List<Attribute> attributes;
        final List<Capability> capabilities;
        final List<Dependency> dependencies;
        final List<DependencyConstraint> dependencyConstraints;
        final List<Artifact> artifacts;

        LocalVariant(
            String name,
            List<Attribute> attributes,
            List<Capability> capabilities,
            List<Dependency> dependencies,
            List<DependencyConstraint> dependencyConstraints,
            List<Artifact> artifacts
        ) {
            this.name = name;
            this.attributes = attributes;
            this.capabilities = capabilities;
            this.dependencies = dependencies;
            this.dependencyConstraints = dependencyConstraints;
            this.artifacts = artifacts;
        }
    }

    static class RemoteVariant extends Variant {

        final String name;
        final List<Attribute> attributes;
        final AvailableAt availableAt;
        final List<Capability> capabilities;

        RemoteVariant(
            String name,
            List<Attribute> attributes,
            AvailableAt availableAt,
            List<Capability> capabilities
        ) {
            this.name = name;
            this.attributes = attributes;
            this.availableAt = availableAt;
            this.capabilities = capabilities;
        }
    }

    static class Dependency {

        final DependencyCoordinates coordinates;
        final Set<ExcludeRule> excludeRules;
        final List<Attribute> attributes;
        final List<Capability> requestedCapabilities;
        final boolean endorseStrictVersions;
        final String reason;
        final ArtifactSelector artifactSelector;

        public Dependency(
            DependencyCoordinates coordinates,
            Set<ExcludeRule> excludeRules,
            List<Attribute> attributes,
            List<Capability> requestedCapabilities,
            boolean endorseStrictVersions,
            String reason,
            ArtifactSelector artifactSelector
        ) {
            this.coordinates = coordinates;
            this.excludeRules = excludeRules;
            this.attributes = attributes;
            this.requestedCapabilities = requestedCapabilities;
            this.endorseStrictVersions = endorseStrictVersions;
            this.reason = reason;
            this.artifactSelector = artifactSelector;
        }
    }

    static abstract class Variant {
    }

    static class Attribute {

        final String name;
        final Object value;

        public Attribute(String name, Object value) {
            this.name = name;
            this.value = value;
        }
    }

    static class Capability {

        final String group;
        final String name;
        @Nullable
        final String version;

        public Capability(String group, String name, @Nullable String version) {
            this.group = group;
            this.name = name;
            this.version = version;
        }
    }

    static class Version {

        @Nullable
        final String requires;
        @Nullable
        final String strictly;
        @Nullable
        final String preferred;
        final List<String> rejectedVersions;

        public Version(
            @Nullable String requires,
            @Nullable String strictly,
            @Nullable String preferred,
            List<String> rejectedVersions
        ) {
            this.requires = requires;
            this.strictly = strictly;
            this.preferred = preferred;
            this.rejectedVersions = rejectedVersions;
        }
    }

    static class DependencyCoordinates {

        final String group;
        final String name;
        final Version version;

        public DependencyCoordinates(
            String group, String name, Version version
        ) {
            this.group = group;
            this.name = name;
            this.version = version;
        }
    }

    static class ArtifactSelector {

        final String name;
        final String type;
        @Nullable
        final String extension;
        @Nullable
        final String classifier;

        public ArtifactSelector(
            String name,
            String type,
            @Nullable String extension,
            @Nullable String classifier
        ) {
            this.name = name;
            this.type = type;
            this.extension = extension;
            this.classifier = classifier;
        }
    }

    static class DependencyConstraint {

        final String group;
        final String module;
        final Version version;
        final List<Attribute> attributes;
        final String reason;

        public DependencyConstraint(
            String group,
            String module,
            Version version,
            List<Attribute> attributes,
            String reason
        ) {
            this.group = group;
            this.module = module;
            this.version = version;
            this.attributes = attributes;
            this.reason = reason;
        }
    }

    static class Artifact {

        final String name;
        final String uri;
        final File file;

        public Artifact(String name, String uri, File file) {
            this.name = name;
            this.uri = uri;
            this.file = file;
        }
    }

    static class AvailableAt {

        final String url;
        final ModuleVersionIdentifier coordinates;

        public AvailableAt(String url, ModuleVersionIdentifier coordinates) {
            this.url = url;
            this.coordinates = coordinates;
        }
    }
}
