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

package org.gradle.api.tasks.diagnostics.internal.configurations.formatter;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ReportArtifact;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ReportAttribute;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ReportCapability;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ReportConfiguration;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ConfigurationReportModel;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ReportSecondaryVariant;
import org.gradle.internal.logging.text.StyledTextOutput;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 *
 * Note that although the name of this class includes "variants", the implementation internals speak of "configurations".
 */
public final class TextConfigurationReportWriter implements ConfigurationReportWriter {
    private final StyledTextOutput output;
    private int depth;

    public TextConfigurationReportWriter(StyledTextOutput output) {
        this.output = output;
    }

    @Override
    public void writeReport(ConfigurationReportSpec spec, ConfigurationReportModel data) {
        depth = 0;
        if (data.getEligibleConfigs().isEmpty()) {
            writeCompleteAbsenceOfResults(spec, data);
        } else {
            if (spec.isSearchForSpecificVariant()) {
                writeSearchResults(spec, data);
            } else {
                if (spec.isShowLegacy()) {
                    writeLegacyResults(spec, data);
                } else {
                    writeNonLegacyResults(spec, data);
                }
            }
        }
    }

    private void writeCompleteAbsenceOfResults(ConfigurationReportSpec spec, ConfigurationReportModel data) {
        message("There are no " + spec.getReportType().getFullReportedTypeDesc() + "s (including legacy " + spec.getReportType().getReportedTypeAlias() + "s) present in project '" + data.getProjectName() + "'.");
    }

    private void writeSearchResults(ConfigurationReportSpec spec, ConfigurationReportModel data) {
        Optional<ReportConfiguration> searchResult = data.getConfigNamed(spec.getSearchTarget().get());
        if (searchResult.isPresent()) {
            writeConfigurations(spec, Collections.singletonList(searchResult.get()));
        } else {
            message("There are no " + spec.getReportType().getFullReportedTypeDesc() + "s on project '" + data.getProjectName() + "' named '" + spec.getSearchTarget().get() + "'.");
        }
    }

    private void writeLegacyResults(ConfigurationReportSpec spec, ConfigurationReportModel data) {
        writeConfigurations(spec, data.getEligibleConfigs());
    }

    private void writeNonLegacyResults(ConfigurationReportSpec spec, ConfigurationReportModel data) {
        List<ReportConfiguration> nonLegacyConfigs = data.getNonLegacyConfigs();
        if (nonLegacyConfigs.isEmpty()) {
            message("There are no proper " + spec.getReportType().getFullReportedTypeDesc() + "s present in project '" + data.getProjectName() + "'.");

            boolean additionalLegacyConfigs = data.getEligibleConfigs().size() > nonLegacyConfigs.size();
            if (additionalLegacyConfigs) {
                message("Re-run this report with the '--all' flag to include legacy " + spec.getReportType().getReportedTypeAlias() + "s (legacy = consumable and resolvable).");
            }
        } else {
            writeConfigurations(spec, nonLegacyConfigs);
        }
    }

    private void writeConfigurations(ConfigurationReportSpec spec, List<ReportConfiguration> configurations) {
        configurations.forEach(c -> writeConfiguration(spec, c));
        writeLegend(configurations);
    }

    private void writeLegend(Collection<ReportConfiguration> configs) {
        boolean hasLegacy =  configs.stream().anyMatch(ReportConfiguration::isLegacy);
        boolean hasIncubating = configs.stream().anyMatch(ReportConfiguration::hasIncubatingAttributes) ||
            configs.stream().flatMap(c -> c.getSecondaryVariants().stream()).anyMatch(ReportSecondaryVariant::hasIncubatingAttributes);
        boolean hasVariants = configs.stream().anyMatch(ReportConfiguration::hasVariants);

        if (hasLegacy) {
            output.println("(l) Legacy or deprecated configuration. Those are variants created for backwards compatibility which are both resolvable and consumable.");
        }
        if (hasIncubating) {
            output.println("(i) Configuration uses incubating attributes such as Category.VERIFICATION.");
        }
        if (hasVariants) {
            output.text("(*) Secondary variants are variants created via the ")
                .style(StyledTextOutput.Style.Identifier)
                .text("Configuration#getOutgoing(): ConfigurationPublications")
                .style(StyledTextOutput.Style.Info)
                .text(" API which also participate in selection, in addition to the configuration itself.")
                .println();
        }
    }

    private void writeConfiguration(ConfigurationReportSpec format, ReportConfiguration config) {
        writeConfigurationNameHeader(config, format.getReportType().getReportedTypeAlias());
        writeConfigurationDescription(config);

        if (!config.getAttributes().isEmpty() ||
            (format.getReportType().isIncludeCapabilities() && !config.getCapabilities().isEmpty()) ||
            (format.getReportType().isIncludeArtifacts() && !config.getArtifacts().isEmpty()) ||
            (format.getReportType().isIncludeVariants() && !config.getSecondaryVariants().isEmpty())) {
            newLine();
        }

        if (format.getReportType().isIncludeCapabilities()) {
            writeCapabilities(config.getCapabilities());
        }

        writeAttributes(config.getAttributes());

        if (format.getReportType().isIncludeArtifacts()) {
            writeArtifacts(config.getArtifacts());
        }

        if (format.getReportType().isIncludeVariants()) {
            writeSecondaryVariants(config.getSecondaryVariants());
        }

        newLine();
    }

    private void writeConfigurationNameHeader(ReportConfiguration config, String targetName) {
        String name = buildNameWithIndicators(config);
        printHeader(StringUtils.capitalize(targetName) + " " + name);
    }

    private void writeConfigurationDescription(ReportConfiguration config) {
        String description = config.getDescription();
        if (description != null) {
            valuePair("Description", description);
            newLine();
        }
    }

    private String buildNameWithIndicators(ReportConfiguration config) {
        String name = config.getName();
        if (config.isLegacy()) {
            name += " (l)";
        }
        if (config.hasIncubatingAttributes()) {
            name += " (i)";
        }
        return name;
    }

    private void writeSecondaryVariants(Set<ReportSecondaryVariant> variants) {
        if (!variants.isEmpty()) {
            newLine();
            printSection("Secondary Variants (*)", () -> {
                variants.stream()
                    .sorted(Comparator.comparing(ReportSecondaryVariant::getName))
                    .forEach(variant -> {
                        newLine();
                        writeSecondaryVariant(variant);
                    });
            });
        }
    }

    private void writeAttributes(Set<ReportAttribute> attributes) {
        if (!attributes.isEmpty()) {
            Integer max = attributes.stream().map(attr -> attr.getName().length()).max(Integer::compare).orElse(0);
            printSection("Attributes", () -> {
                attributes.stream()
                    .sorted(Comparator.comparing(ReportAttribute::getName))
                    .forEach(attr -> {
                        bulletIndent();
                        valuePair(StringUtils.rightPad(attr.getName(), max), String.valueOf(attr.getValue()));
                        newLine();
                    });
            });
        }
    }

    private void writeArtifacts(Collection<ReportArtifact> artifacts) {
        if (!artifacts.isEmpty()) {
            printSection("Artifacts", () -> {
                artifacts.stream()
                    .sorted(Comparator.comparing(ReportArtifact::getDisplayName))
                    .forEach(art -> {
                        bulletIndent();
                        writeArtifact(art);
                        newLine();
                    });
            });
        }
    }

    private void writeArtifact(ReportArtifact artifact) {
        String type = artifact.getType();
        output.text(artifact.getDisplayName());
        if (StringUtils.isNotEmpty(type)) {
            output.text(" (");
            valuePair("artifactType", type);
            output.text(")");
        }
    }

    private void writeCapabilities(Set<ReportCapability> capabilities) {
        if (!capabilities.isEmpty()) {
            printSection("Capabilities", () -> {
                capabilities.stream()
                    .map(cap -> String.format("%s:%s:%s%s", cap.getGroup(), cap.getModule(), cap.getVersion(), cap.isDefault() ? " (default capability)" : ""))
                    .sorted()
                    .forEach(cap -> {
                        bulletIndent();
                        output.text(cap);
                        newLine();
                    });
            });
        }
    }

    private String buildSecondaryVariantNameWithIndicators(ReportSecondaryVariant config) {
        String name = config.getName();
        if (config.hasIncubatingAttributes()) {
            name += " (i)";
        }
        return name += " (*)";
    }

    private void writeSecondaryVariant(ReportSecondaryVariant variant) {
        printHeader("Secondary Variant " + buildSecondaryVariantNameWithIndicators(variant));
        try {
            depth++;
            writeAttributes(variant.getAttributes());
            writeArtifacts(variant.getArtifacts());
        } finally {
            depth--;
        }
    }

    private void printHeader(String text) {
        output.style(StyledTextOutput.Style.Header);
        output.text(StringUtils.repeat("    ", depth));
        output.println("--------------------------------------------------");
        output.text(StringUtils.repeat("    ", depth));
        output.println(text);
        output.text(StringUtils.repeat("    ", depth));
        output.println("--------------------------------------------------");
        output.style(StyledTextOutput.Style.Normal);
    }

    private void printSection(String title, Runnable action) {
        printSection(title, null, action);
    }

    private void printSection(String title, @Nullable String description, Runnable action) {
        output.text(StringUtils.repeat("    ", depth));
        output.style(StyledTextOutput.Style.Description);
        output.text(title);
        output.style(StyledTextOutput.Style.Normal);
        if (description != null) {
            output.text(" : " + description);
        }
        try {
            depth++;
            newLine();
            action.run();
        } finally {
            depth--;
        }
    }

    private void valuePair(String key, String value) {
        output.style(StyledTextOutput.Style.Identifier);
        output.text(key);
        output.style(StyledTextOutput.Style.Normal)
            .text(" = " + value);
    }

    private void bulletIndent() {
        output.text(StringUtils.repeat("    ", depth));
        if (depth > 0) {
            output.withStyle(StyledTextOutput.Style.Normal).text("- ");
        }
    }

    private void message(String msg) {
        output.text(msg).println();
    }

    private void newLine() {
        output.println();
    }
}
