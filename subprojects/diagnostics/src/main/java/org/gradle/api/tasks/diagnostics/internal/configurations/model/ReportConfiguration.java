/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.configurations.model;

import org.gradle.api.Project;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.file.FileResolver;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.stream.Collectors;

public final class ReportConfiguration {
    private final String name;
    @Nullable
    private final String description;
    private final Type type;
    private final Set<ReportAttribute> attributes;
    private final Set<ReportCapability> capabilities;
    private final Set<ReportArtifact> artifacts;
    private final Set<ReportSecondaryVariant> variants;

    private ReportConfiguration(String name, @Nullable String description, Type type, Set<ReportAttribute> attributes, Set<ReportCapability> capabilities, Set<ReportArtifact> artifacts, Set<ReportSecondaryVariant> variants) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.attributes = attributes;
        this.capabilities = capabilities;
        this.artifacts = artifacts;
        this.variants = variants;
    }

    public static ReportConfiguration fromConfigurationInProject(ConfigurationInternal configuration, Project project, FileResolver fileResolver) {
        // Important to lock the config prior to extracting the attributes, as some attributes, such as TargetJvmVersion, are actually added by this locking process
        configuration.preventFromFurtherMutation();

        Set<ReportAttribute> attributes = configuration.getAttributes().keySet().stream().map(a -> ReportAttribute.fromAttributeInContainer(a, configuration.getAttributes())).collect(Collectors.toSet());
        Set<ReportCapability> capabilities = configuration.getOutgoing().getCapabilities().stream().map(ReportCapability::fromCapability).collect(Collectors.toSet());
        if (capabilities.isEmpty()) {
            capabilities.add(ReportCapability.defaultCapability(project));
        }
        Set<ReportArtifact> artifacts = configuration.getAllArtifacts().stream().map(a -> ReportArtifact.fromPublishArtifact(a, fileResolver)).collect(Collectors.toSet());
        Set<ReportSecondaryVariant> variants = configuration.getOutgoing().getVariants().stream().map(v -> ReportSecondaryVariant.fromConfigurationVariant(v, fileResolver)).collect(Collectors.toSet());
        Type type = configuration.isCanBeConsumed() ? (configuration.isCanBeResolved() ? Type.LEGACY : Type.CONSUMABLE) : Type.RESOLVABLE;

        return new ReportConfiguration(configuration.getName(), configuration.getDescription(), type, attributes, capabilities, artifacts, variants);
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public Type getType() {
        return type;
    }

    public Set<ReportAttribute> getAttributes() {
        return attributes;
    }

    public Set<ReportCapability> getCapabilities() {
        return capabilities;
    }

    public Set<ReportArtifact> getArtifacts() {
        return artifacts;
    }

    public Set<ReportSecondaryVariant> getVariants() {
        return variants;
    }

    public boolean isLegacy() {
        return Type.LEGACY == type;
    }

    public boolean hasIncubatingAttributes() {
        return attributes.stream().anyMatch(ReportAttribute::isIncubating);
    }

    public boolean hasVariants() {
        return !variants.isEmpty();
    }

    public enum Type {
        CONSUMABLE,
        RESOLVABLE,
        LEGACY
    }
}
