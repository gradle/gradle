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

import com.google.gson.stream.JsonWriter;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.util.GradleVersion;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.gradle.util.internal.GUtil.elvis;

class ModuleMetadataJsonWriter extends JsonWriterScope {

    private final ModuleMetadataSpec metadata;
    @Nullable
    private final String buildId;
    private final ChecksumService checksumService;

    public ModuleMetadataJsonWriter(
        JsonWriter jsonWriter,
        ModuleMetadataSpec metadata,
        @Nullable String buildId,
        ChecksumService checksumService
    ) {
        super(jsonWriter);
        this.metadata = metadata;
        this.buildId = buildId;
        this.checksumService = checksumService;
    }

    void write() throws IOException {
        writeObject(() -> {
            writeFormat();
            writeIdentity();
            writeCreator();
            writeVariants();
        });
    }

    private void writeFormat() throws IOException {
        write("formatVersion", GradleModuleMetadataParser.FORMAT_VERSION);
    }

    private void writeIdentity() throws IOException {
        writeObject("component", () -> {
            ModuleMetadataSpec.Identity identity = metadata.identity;
            if (identity.relativeUrl != null) {
                write("url", identity.relativeUrl);
            }
            writeCoordinates(identity.coordinates);
            writeAttributes(identity.attributes);
        });
    }

    private void writeCreator() throws IOException {
        writeObject("createdBy", () ->
            writeObject("gradle", () -> {
                write("version", GradleVersion.current().getVersion());
                if (buildId != null) {
                    write("buildId", buildId);
                }
            })
        );
    }

    private void writeVariants() throws IOException {
        List<ModuleMetadataSpec.Variant> variants = metadata.variants;
        if (variants.isEmpty()) {
            return;
        }
        writeArray("variants", () -> {
            for (ModuleMetadataSpec.Variant variant : variants) {
                if (variant instanceof ModuleMetadataSpec.LocalVariant) {
                    ModuleMetadataSpec.LocalVariant local = (ModuleMetadataSpec.LocalVariant) variant;
                    writeObject(() -> {
                        write("name", local.name);
                        writeAttributes(local.attributes);
                        writeDependencies(local.dependencies);
                        writeDependencyConstraints(local.dependencyConstraints);
                        writeArtifacts(local.artifacts);
                        writeCapabilities("capabilities", local.capabilities);
                    });
                    continue;
                }
                if (variant instanceof ModuleMetadataSpec.RemoteVariant) {
                    ModuleMetadataSpec.RemoteVariant remote = (ModuleMetadataSpec.RemoteVariant) variant;
                    writeObject(() -> {
                        write("name", remote.name);
                        writeAttributes(remote.attributes);
                        writeAvailableAt(remote.availableAt);
                        writeCapabilities("capabilities", remote.capabilities);
                    });
                    continue;
                }
                throw new IllegalStateException("Unknown variant type: " + variant);
            }
        });
    }

    private void writeNonEmptyAttributes(List<ModuleMetadataSpec.Attribute> attributes) throws IOException {
        if (!attributes.isEmpty()) {
            writeAttributes(attributes);
        }
    }

    private void writeAttributes(List<ModuleMetadataSpec.Attribute> attributes) throws IOException {
        writeObject("attributes", () -> {
            for (ModuleMetadataSpec.Attribute attribute : attributes) {
                writeAttribute(attribute.name, attribute.value);
            }
        });
    }

    private void writeAttribute(String name, Object value) throws IOException {
        if (value instanceof Boolean) {
            write(name, (Boolean) value);
        } else if (value instanceof Integer) {
            write(name, (Integer) value);
        } else if (value instanceof String) {
            write(name, (String) value);
        } else {
            throw new IllegalArgumentException("value");
        }
    }

    private void writeCapabilities(String key, List<ModuleMetadataSpec.Capability> capabilities) throws IOException {
        if (capabilities.isEmpty()) {
            return;
        }
        writeArray(key, () -> {
            for (ModuleMetadataSpec.Capability capability : capabilities) {
                writeObject(() -> {
                    write("group", capability.group);
                    write("name", capability.name);
                    if (capability.version != null) {
                        write("version", capability.version);
                    }
                });
            }
        });
    }

    private void writeAvailableAt(ModuleMetadataSpec.AvailableAt availableAt) throws IOException {
        writeObject("available-at", () -> {
            write("url", availableAt.url);
            writeCoordinates(availableAt.coordinates);
        });
    }

    private void writeCoordinates(ModuleVersionIdentifier coordinates) throws IOException {
        write("group", coordinates.getGroup());
        write("module", coordinates.getName());
        write("version", coordinates.getVersion());
    }

    private void writeArtifacts(List<ModuleMetadataSpec.Artifact> artifacts) throws IOException {
        if (artifacts.isEmpty()) {
            return;
        }
        writeArray("files", () -> {
            for (ModuleMetadataSpec.Artifact artifact : artifacts) {
                writeObject(() -> {
                    write("name", artifact.name);
                    write("url", artifact.uri);
                    File file = artifact.file;
                    write("size", file.length());
                    write("sha512", sha512(file));
                    write("sha256", sha256(file));
                    write("sha1", sha1(file));
                    write("md5", md5(file));
                });
            }
        });
    }

    private void writeDependencies(List<ModuleMetadataSpec.Dependency> dependencies) throws IOException {
        if (dependencies.isEmpty()) {
            return;
        }
        writeArray("dependencies", () -> {
            for (ModuleMetadataSpec.Dependency moduleDependency : dependencies) {
                writeObject(() -> {
                    ModuleMetadataSpec.DependencyCoordinates identifier = moduleDependency.coordinates;
                    write("group", identifier.group);
                    write("module", identifier.name);
                    writeVersionConstraint(identifier.version);
                    writeExcludes(moduleDependency.excludeRules);
                    writeNonEmptyAttributes(moduleDependency.attributes);
                    writeCapabilities("requestedCapabilities", moduleDependency.requestedCapabilities);
                    if (moduleDependency.endorseStrictVersions) {
                        write("endorseStrictVersions", true);
                    }
                    if (moduleDependency.reason != null) {
                        write("reason", moduleDependency.reason);
                    }
                    if (moduleDependency.artifactSelector != null) {
                        writeDependencyArtifact(moduleDependency.artifactSelector);
                    }
                });
            }
        });
    }

    private void writeVersionConstraint(@Nullable ModuleMetadataSpec.Version version) throws IOException {
        if (version == null) {
            return;
        }
        writeObject("version", () -> {
            if (version.strictly != null) {
                write("strictly", version.strictly);
            }
            if (version.requires != null) {
                write("requires", version.requires);
            }
            if (version.preferred != null) {
                write("prefers", version.preferred);
            }
            if (!version.rejectedVersions.isEmpty()) {
                writeArray("rejects", version.rejectedVersions);
            }
        });
    }

    private void writeDependencyArtifact(ModuleMetadataSpec.ArtifactSelector artifactSelector) throws IOException {
        writeObject("thirdPartyCompatibility", () ->
            writeObject("artifactSelector", () -> {
                write("name", artifactSelector.name);
                write("type", artifactSelector.type);
                if (artifactSelector.extension != null) {
                    write("extension", artifactSelector.extension);
                }
                if (artifactSelector.classifier != null) {
                    write("classifier", artifactSelector.classifier);
                }
            })
        );
    }

    private void writeDependencyConstraints(List<ModuleMetadataSpec.DependencyConstraint> constraints) throws IOException {
        if (constraints.isEmpty()) {
            return;
        }
        writeArray("dependencyConstraints", () -> {
            for (ModuleMetadataSpec.DependencyConstraint constraint : constraints) {
                writeObject(() -> {
                    write("group", constraint.group);
                    write("module", constraint.module);
                    writeVersionConstraint(constraint.version);
                    writeNonEmptyAttributes(constraint.attributes);
                    if (constraint.reason != null) {
                        write("reason", constraint.reason);
                    }
                });
            }
        });
    }

    private void writeExcludes(Set<ExcludeRule> excludeRules) throws IOException {
        if (excludeRules.isEmpty()) {
            return;
        }
        writeArray("excludes", () -> {
            for (ExcludeRule excludeRule : excludeRules) {
                writeObject(() -> {
                    write("group", elvis(excludeRule.getGroup(), "*"));
                    write("module", elvis(excludeRule.getModule(), "*"));
                });
            }
        });
    }

    private String md5(File file) {
        return checksumService.md5(file).toString();
    }

    private String sha1(File file) {
        return checksumService.sha1(file).toString();
    }

    private String sha256(File file) {
        return checksumService.sha256(file).toString();
    }

    private String sha512(File file) {
        return checksumService.sha512(file).toString();
    }
}
