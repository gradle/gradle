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
        text("There are no " + spec.getReportType().getFullReportedTypeDesc() + "s (including legacy " + spec.getReportType().getReportedTypeAlias() + "s) present in project '" + data.getProjectName() + "'.");
        newLine();
    }

    private void writeSearchResults(ConfigurationReportSpec spec, ConfigurationReportModel data) {
        Optional<ReportConfiguration> searchResult = data.getConfigNamed(spec.getSearchTarget().get());
        if (searchResult.isPresent()) {
            writeConfigurations(spec, Collections.singletonList(searchResult.get()));
        } else {
            text("There are no " + spec.getReportType().getFullReportedTypeDesc() + "s on project '" + data.getProjectName() + "' named '" + spec.getSearchTarget().get() + "'.");
        }
        newLine();
    }

    private void writeLegacyResults(ConfigurationReportSpec spec, ConfigurationReportModel data) {
        writeConfigurations(spec, data.getEligibleConfigs());
        newLine();
    }

    private void writeNonLegacyResults(ConfigurationReportSpec spec, ConfigurationReportModel data) {
        List<ReportConfiguration> nonLegacyConfigs = data.getNonLegacyConfigs();
        if (nonLegacyConfigs.isEmpty()) {
            text("There are no proper " + spec.getReportType().getFullReportedTypeDesc() + "s present in project '" + data.getProjectName() + "'.");
            newLine();

            boolean additionalLegacyConfigs = data.getEligibleConfigs().size() > nonLegacyConfigs.size();
            if (additionalLegacyConfigs) {
                text("Re-run this report with the '--all' flag to include legacy " + spec.getReportType().getReportedTypeAlias() + "s (legacy = consumable and resolvable).");
            }
        } else {
            writeConfigurations(spec, nonLegacyConfigs);
        }
        newLine();
    }

    private void writeConfigurations(ConfigurationReportSpec spec, List<ReportConfiguration> configurations) {
        configurations.forEach(c -> writeConfiguration(spec, c));
        writeLegend(configurations);
    }

    private void writeLegend(Collection<ReportConfiguration> configs) {
        boolean hasLegacy =  configs.stream().anyMatch(ReportConfiguration::isLegacy);
        boolean hasIncubating = configs.stream().anyMatch(ReportConfiguration::hasIncubatingAttributes);
        boolean hasVariants = configs.stream().anyMatch(ReportConfiguration::hasVariants);

        if (hasLegacy || hasVariants) {
            output.println();
        }
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

        if (format.getReportType().isIncludeCapabilities()) {
            // Preserve existing behavior where capabilities are not printed if no attributes
            if (!config.getAttributes().isEmpty()) {
                writeCapabilities(config.getCapabilities());
                writeAttributes(config.getAttributes());
            }
        } else {
            if (!config.getAttributes().isEmpty()) {
                writeAttributes(config.getAttributes());
                newLine();
            }
        }

        if (format.getReportType().isIncludeArtifacts()) {
            if (!config.getArtifacts().isEmpty() && !config.getAttributes().isEmpty()) {
                newLine(); // Preserve formatting for now
            }
            writeArtifacts(config.getArtifacts());
            newLine(); // Preserve formatting
        }

        if (format.getReportType().isIncludeVariants()) {
            writeSecondaryVariants(config.getVariants());
        }
    }

    private void writeConfigurationNameHeader(ReportConfiguration config, String targetName) {
        String name = buildNameWithIndicators(config);
        printHeader(StringUtils.capitalize(targetName) + " " + name, output);
    }

    private void writeConfigurationDescription(ReportConfiguration config) {
        String description = config.getDescription();
        if (description != null) {
            printValue("Description", description);
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
            printSection("Secondary variants (*)", () -> {
                variants.stream()
                    .sorted(Comparator.comparing(ReportSecondaryVariant::getName))
                    .forEach(variant -> printSection("Variant", variant.getName(), () -> {
                        writeAttributes(variant.getAttributes());
                        writeArtifacts(variant.getArtifacts());
                    }));
            });
            newLine();
        }
    }

    private void writeAttributes(Set<ReportAttribute> attributes) {
        if (!attributes.isEmpty()) {
            Integer max = attributes.stream().map(attr -> attr.getName().length()).max(Integer::compare).orElse(0);
            printSection("Attributes", () -> {
                attributes.stream()
                    .sorted(Comparator.comparing(ReportAttribute::getName))
                    .forEach(attr ->
                        printValue(StringUtils.rightPad(attr.getName(), max), String.valueOf(attr.getValue()))
                    );
            });
        }
    }

    private void writeArtifacts(Collection<ReportArtifact> artifacts) {
        if (!artifacts.isEmpty()) {
            printSection("Artifacts", () -> {
                artifacts.stream()
                    .sorted(Comparator.comparing(ReportArtifact::getDisplayName))
                    .forEach(this::writeArtifact);
            });
        }
    }

    private void writeArtifact(ReportArtifact artifact) {
        String type = artifact.getType();
        text(artifact.getDisplayName());
        if (StringUtils.isNotEmpty(type)) {
            output.text(" (");
            printValue("artifactType", type);
            output.text(")");
        }
        newLine();
    }

    private void writeCapabilities(Set<ReportCapability> capabilities) {
        printSection("Capabilities", () -> {
            capabilities.stream()
                .map(cap -> String.format("%s:%s:%s%s", cap.getGroup(), cap.getModule(), cap.getVersion(), cap.isDefault() ? " (default capability)" : ""))
                .sorted()
                .forEach(cap -> {
                    text(cap);
                    newLine();
                });
        });
    }

    private void printHeader(String text, StyledTextOutput output) {
        output.style(StyledTextOutput.Style.Header)
            .println("--------------------------------------------------")
            .println(text)
            .println("--------------------------------------------------")
            .style(StyledTextOutput.Style.Normal);
    }

    private void printSection(String title, Runnable action) {
        printSection(title, null, action);
    }

    private void printSection(String title, @Nullable String description, Runnable action) {
        output.style(StyledTextOutput.Style.Description);
        text(title);
        output.style(StyledTextOutput.Style.Normal);
        if (description != null) {
            output.text(" : " + description);
        }
        try {
            depth++;
            output.println();
            action.run();
        } finally {
            depth--;
        }
    }

    private void printValue(String key, String value) {
        output.style(StyledTextOutput.Style.Identifier);
        text(key);
        output.style(StyledTextOutput.Style.Normal)
            .println(" = " + value);
    }

    private void text(String text) {
        output.text(StringUtils.repeat("   ", depth));
        if (depth > 0) {
            output.withStyle(StyledTextOutput.Style.Normal).text(" - ");
        }
        output.text(text);
    }

    private void newLine() {
        output.println();
    }
}
