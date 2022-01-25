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

import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.internal.file.FileResolver;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Lightweight, immutable model of a secondary variant of a configuration for configuration reporting.
 */
public final class ReportSecondaryVariant {
    private final String name;
    @Nullable
    private final String description;
    private final List<ReportAttribute> attributes;
    private final List<ReportArtifact> artifacts;

    private ReportSecondaryVariant(String name, @Nullable String description, List<ReportAttribute> attributes, List<ReportArtifact> artifacts) {
        this.name = name;
        this.description = description;
        this.attributes = attributes;
        this.artifacts = artifacts;
    }

    public static ReportSecondaryVariant fromConfigurationVariant(ConfigurationVariant variant, FileResolver fileResolver) {
        final List<ReportAttribute> attributes = Collections.unmodifiableList(variant.getAttributes().keySet().stream()
            .map(a -> ReportAttribute.fromAttributeInContainer(a, variant.getAttributes()))
            .sorted(Comparator.comparing(ReportAttribute::getName))
            .collect(Collectors.toList()));
        final List<ReportArtifact> artifacts = Collections.unmodifiableList(variant.getArtifacts().stream()
            .map(publishArtifact -> ReportArtifact.fromPublishArtifact(publishArtifact, fileResolver))
            .sorted(Comparator.comparing(ReportArtifact::getName))
            .collect(Collectors.toList()));
        return new ReportSecondaryVariant(variant.getName(), variant.getDescription().orElse(null), attributes, artifacts);
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public List<ReportAttribute> getAttributes() {
        return attributes;
    }

    public List<ReportArtifact> getArtifacts() {
        return artifacts;
    }

    public boolean hasIncubatingAttributes() {
        return attributes.stream().anyMatch(ReportAttribute::isIncubating);
    }
}
