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

import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Lightweight, immutable model of a configuration for configuration reporting.
 */
public final class ReportConfiguration {
    private final String name;
    @Nullable
    private final String description;
    @Nullable
    private final Type type;
    private final ImmutableList<ReportAttribute> attributes;
    private final ImmutableList<ReportCapability> capabilities;
    private final ImmutableList<ReportArtifact> artifacts;
    private final ImmutableList<ReportSecondaryVariant> variants;
    private final ImmutableList<ReportConfiguration> extendedConfigurations;

    ReportConfiguration(String name, @Nullable String description, @Nullable Type type,
                                List<ReportAttribute> attributes,
                                List<ReportCapability> capabilities,
                                List<ReportArtifact> artifacts,
                                List<ReportSecondaryVariant> variants,
                                List<ReportConfiguration> extendedConfigurations) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.attributes = ImmutableList.copyOf(attributes);
        this.capabilities = ImmutableList.copyOf(capabilities);
        this.artifacts = ImmutableList.copyOf(artifacts);
        this.variants = ImmutableList.copyOf(variants);
        this.extendedConfigurations = ImmutableList.copyOf(extendedConfigurations);
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    @Nullable
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

    public List<ReportConfiguration> getExtendedConfigurations() {
        return extendedConfigurations;
    }

    public boolean isPurelyConsumable() {
        return Type.CONSUMABLE == type;
    }

    public boolean isPurelyResolvable() {
        return Type.RESOLVABLE == type;
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
