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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.file.FileResolver;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class ReportConfiguration {
    private final String name;
    @Nullable
    private final String description;
    private final Type type;
    private final List<ReportAttribute> attributes;
    private final List<ReportCapability> capabilities;
    private final List<ReportArtifact> artifacts;
    private final List<ReportSecondaryVariant> variants;
    private final List<String> extendedConfigurations;

    private ReportConfiguration(String name, @Nullable String description, Type type,
                                List<ReportAttribute> attributes,
                                List<ReportCapability> capabilities,
                                List<ReportArtifact> artifacts,
                                List<ReportSecondaryVariant> variants,
                                List<String> extendedConfigurations) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.attributes = Collections.unmodifiableList(attributes);
        this.capabilities = Collections.unmodifiableList(capabilities);
        this.artifacts = Collections.unmodifiableList(artifacts);
        this.variants = Collections.unmodifiableList(variants);
        this.extendedConfigurations = Collections.unmodifiableList(extendedConfigurations);
    }

    public static ReportConfiguration fromConfigurationInProject(ConfigurationInternal configuration, Project project, FileResolver fileResolver) {
        // Important to lock the config prior to extracting the attributes, as some attributes, such as TargetJvmVersion, are actually added by this locking process
        configuration.preventFromFurtherMutation();

        final List<ReportAttribute> attributes = configuration.getAttributes().keySet().stream()
            .map(a -> ReportAttribute.fromAttributeInContainer(a, configuration.getAttributes()))
            .sorted(Comparator.comparing(ReportAttribute::getName))
            .collect(Collectors.toList());

        final List<ReportCapability> explicitCapabilities = configuration.getOutgoing().getCapabilities().stream()
            .map(ReportCapability::fromCapability)
            .sorted(Comparator.comparing(ReportCapability::toGAV))
            .collect(Collectors.toList());
        final List<ReportCapability> capabilities;
        if (explicitCapabilities.isEmpty()) {
            capabilities = Collections.singletonList(ReportCapability.defaultCapability(project));
        } else {
            capabilities = explicitCapabilities;
        }

        final List<ReportArtifact> artifacts = Collections.unmodifiableList(configuration.getAllArtifacts().stream()
            .map(a -> ReportArtifact.fromPublishArtifact(a, fileResolver))
            .sorted(Comparator.comparing(ReportArtifact::getDisplayName))
            .collect(Collectors.toList()));

        final List<ReportSecondaryVariant> variants = configuration.getOutgoing().getVariants().stream()
            .map(v -> ReportSecondaryVariant.fromConfigurationVariant(v, fileResolver))
            .sorted(Comparator.comparing(ReportSecondaryVariant::getName))
            .collect(Collectors.toList());

        final List<String> extendedConfigurations = configuration.getExtendsFrom().stream()
            .map(Configuration::getName)
            .sorted()
            .collect(Collectors.toList());

        final Type type = configuration.isCanBeConsumed() ? (configuration.isCanBeResolved() ? Type.LEGACY : Type.CONSUMABLE) : Type.RESOLVABLE;
        return new ReportConfiguration(configuration.getName(), configuration.getDescription(), type, attributes, capabilities, artifacts, variants, extendedConfigurations);
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

    public List<ReportAttribute> getAttributes() {
        return attributes;
    }

    public List<ReportCapability> getCapabilities() {
        return capabilities;
    }

    public List<ReportArtifact> getArtifacts() {
        return artifacts;
    }

    public List<ReportSecondaryVariant> getSecondaryVariants() {
        return variants;
    }

    public List<String> getExtendedConfigurations() {
        return extendedConfigurations;
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
