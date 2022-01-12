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

package org.gradle.api.tasks.diagnostics.internal.variantreports.formatter;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.tasks.diagnostics.internal.variantreports.model.ReportArtifact;
import org.gradle.api.tasks.diagnostics.internal.variantreports.model.ReportAttribute;
import org.gradle.api.tasks.diagnostics.internal.variantreports.model.ReportCapability;
import org.gradle.api.tasks.diagnostics.internal.variantreports.model.ReportConfiguration;
import org.gradle.api.tasks.diagnostics.internal.variantreports.model.ReportSecondaryVariant;
import org.gradle.internal.logging.text.StyledTextOutput;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class ConsoleVariantReportWriter extends AbstractVariantReportWriter {
    private final StyledTextOutput output;
    private int depth;

    private final String projectName;
    private final String targetName;
    private final String direction;
    private final boolean includeCapabilities;
    private final boolean includeArtifacts;
    private final boolean includeSecondaryVariants;

    private ConsoleVariantReportWriter(StyledTextOutput output, String projectName, String targetName, String direction, boolean includeCapabilities, boolean includeArtifacts, boolean includeSecondaryVariants) {
        this.output = output;
        this.projectName = projectName;
        this.targetName = targetName;
        this.direction = direction;
        this.includeCapabilities = includeCapabilities;
        this.includeArtifacts = includeArtifacts;
        this.includeSecondaryVariants = includeSecondaryVariants;
    }

    public static ConsoleVariantReportWriter outgoingVariants(StyledTextOutput output, String projectName) {
        return new ConsoleVariantReportWriter(output, projectName, "variant", "outgoing", true, true, true);
    }

    public static ConsoleVariantReportWriter resolvableConfigurations(StyledTextOutput output, String projectName) {
        return new ConsoleVariantReportWriter(output, projectName, "configuration", "resolvable", false, false, false);
    }

    @Override
    public void writeReport(Optional<String> searchTarget, List<ReportConfiguration> matchingConfigs, List<ReportConfiguration> allConfigs) {
        depth = 0;
        if (matchingConfigs.isEmpty()) {
            writeNoMatches(searchTarget.get(), allConfigs);
        } else {
            writeMatches(matchingConfigs);
        }
    }

    private void writeNoMatches(String searchTarget, List<ReportConfiguration> configs) {
        text("There is no " + targetName + " named '" + searchTarget + "' defined on this project.");
        newLine();
        if (configs.isEmpty()) {
            output.println("There are no " + direction + " " + targetName + "s on project " + projectName);
        } else {
            output.println("Here are the available " + direction + " " + targetName + "s: " + configs.stream().map(ReportConfiguration::getName).collect(Collectors.joining(", ")));
        }
    }

    private void writeMatches(List<ReportConfiguration> configs) {
        configs.forEach(this::writeConfiguration);
        writeLegend(configs);
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

    private void writeConfiguration(ReportConfiguration config) {
        writeConfigurationNameHeader(config);
        writeConfigurationDescription(config);

        if (includeCapabilities) {
            // Preserve existing behavior where capabilities are not printed if no attributes
            if (!config.getAttributes().isEmpty()) {
                writeCapabilities(config.getCapabilities());
                writeAttributes(config.getAttributes());
            }
        } else {
            if (!config.getAttributes().isEmpty()) {
                writeAttributes(config.getAttributes());
            }
        }

        if (includeArtifacts) {
            if (!config.getArtifacts().isEmpty() && !config.getAttributes().isEmpty()) {
                newLine(); // Preserve formatting for now
            }
            writeArtifacts(config.getArtifacts());
        }
        newLine(); // Preserve formatting

        if (includeSecondaryVariants) {
            writeSecondaryVariants(config.getVariants());
        }
    }

    private void writeConfigurationNameHeader(ReportConfiguration config) {
        String name = buildNameWithIndicators(config);
        header(StringUtils.capitalize(targetName) + " " + name, output);
    }

    private void writeConfigurationDescription(ReportConfiguration config) {
        String description = config.getDescription();
        if (description != null) {
            value("Description", description);
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
            section("Secondary variants (*)", () -> {
                variants.stream()
                    .sorted(Comparator.comparing(ReportSecondaryVariant::getName))
                    .forEach(variant -> section("Variant", variant.getName(), () -> {
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
            section("Attributes", () -> {
                attributes.stream()
                    .sorted(Comparator.comparing(ReportAttribute::getName))
                    .forEach(attr ->
                        value(StringUtils.rightPad(attr.getName(), max), String.valueOf(attr.getValue()))
                    );
            });
        }
    }

    private void writeArtifacts(Collection<ReportArtifact> artifacts) {
        if (!artifacts.isEmpty()) {
            section("Artifacts", () -> {
                artifacts.stream()
                    .sorted(Comparator.comparing(ReportArtifact::getDisplayName))
                    .forEach(artifact -> writeArtifact(artifact));
            });
        }
    }

    private void writeArtifact(ReportArtifact artifact) {
        String type = artifact.getType();
        text(artifact.getDisplayName());
        if (StringUtils.isNotEmpty(type)) {
            append(" (");
            appendValue("artifactType", type);
            append(")");
        }
        newLine();
    }

    private void writeCapabilities(Set<ReportCapability> capabilities) {
        section("Capabilities", () -> {
            capabilities.stream()
                .map(cap -> String.format("%s:%s:%s%s", cap.getGroup(), cap.getModule(), cap.getVersion(), cap.isDefault() ? " (default capability)" : ""))
                .sorted()
                .forEach(cap -> {
                    text(cap);
                    newLine();
                });
        });
    }

    private void header(String text, StyledTextOutput output) {
        output.style(StyledTextOutput.Style.Header)
            .println("--------------------------------------------------")
            .println(text)
            .println("--------------------------------------------------")
            .style(StyledTextOutput.Style.Normal);
    }

    private void section(String title, Runnable action) {
        section(title, null, action);
    }

    private void section(String title, @Nullable String description, Runnable action) {
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

    private void value(String key, String value) {
        output.style(StyledTextOutput.Style.Identifier);
        text(key);
        output.style(StyledTextOutput.Style.Normal)
            .println(" = " + value);
    }

    private void append(String text) {
        output.text(text);
    }

    private void appendValue(String key, String value) {
        output.style(StyledTextOutput.Style.Identifier);
        append(key);
        output.style(StyledTextOutput.Style.Normal)
            .text(" = " + value);
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
