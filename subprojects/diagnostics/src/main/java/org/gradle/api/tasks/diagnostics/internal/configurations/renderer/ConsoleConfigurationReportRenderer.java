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

package org.gradle.api.tasks.diagnostics.internal.configurations.renderer;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ConfigurationReportModel;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ReportArtifact;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ReportAttribute;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ReportCapability;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ReportConfiguration;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ReportSecondaryVariant;
import org.gradle.api.tasks.diagnostics.internal.configurations.spec.AbstractConfigurationReportSpec;
import org.gradle.internal.logging.text.StyledTextOutput;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The {@link AbstractConfigurationReportRenderer} extension that can be used to render a {@link ConfigurationReportModel}
 * to the console with richly formatted output.
 */
public final class ConsoleConfigurationReportRenderer extends AbstractConfigurationReportRenderer<StyledTextOutput> {
    @Nullable private StyledTextOutput output;
    private int depth;
    private boolean recursiveExtensionsPrinted;

    public ConsoleConfigurationReportRenderer(AbstractConfigurationReportSpec spec) {
        super(spec);
    }

    @Override
    public void render(ConfigurationReportModel data, StyledTextOutput output) {
        this.depth = 0;
        this.output = output;
        this.recursiveExtensionsPrinted = false;

        final boolean hasAnyRelevantConfigs = data.getAllConfigs().stream().anyMatch(c -> spec.isPurelyCorrectType(c) || c.isLegacy());
        if (hasAnyRelevantConfigs) {
            if (spec.isSearchForSpecificVariant()) {
                writeSearchResults(data);
            } else {
                if (spec.isShowLegacy()) {
                    writeLegacyResults(data);
                } else {
                    writeNonLegacyResults(data);
                }
            }
        } else {
            writeCompleteAbsenceOfResults(data);
        }
    }

    private void writeCompleteAbsenceOfResults(ConfigurationReportModel data) {
        message("There are no " + spec.getFullReportedTypeDesc() + "s (including legacy " + spec.getReportedTypeAlias() + "s) present in project '" + data.getProjectName() + "'.");
    }

    private void writeSearchResults(ConfigurationReportModel data) {
        Optional<ReportConfiguration> searchResult = data.getConfigNamed(spec.getSearchTarget().get());
        if (searchResult.isPresent()) {
            writeResults(data, Collections.singletonList(searchResult.get()));
        } else {
            message("There are no " + spec.getFullReportedTypeDesc() + "s on project '" + data.getProjectName() + "' named '" + spec.getSearchTarget().get() + "'.");
        }
    }

    private void writeLegacyResults(ConfigurationReportModel data) {
        final List<ReportConfiguration> legacyConfigs = data.getAllConfigs().stream()
            .filter(c -> c.isLegacy() || spec.isPurelyCorrectType(c))
            .collect(Collectors.toList());
        writeResults(data, legacyConfigs);
    }

    private void writeNonLegacyResults(ConfigurationReportModel data) {
        final List<ReportConfiguration> nonLegacyConfigs = data.getAllConfigs().stream()
            .filter(spec::isPurelyCorrectType)
            .collect(Collectors.toList());
        if (nonLegacyConfigs.isEmpty()) {
            message("There are no purely " + spec.getReportedConfigurationDirection() + " " + spec.getReportedTypeAlias() + "s present in project '" + data.getProjectName() + "'.");

            final boolean hasLegacyConfigs = data.getAllConfigs().stream().anyMatch(ReportConfiguration::isLegacy);
            if (hasLegacyConfigs) {
                message("Re-run this report with the '--all' flag to include legacy " + spec.getReportedTypeAlias() + "s (legacy = consumable and resolvable).");
            }
        } else {
            writeResults(data, nonLegacyConfigs);
        }
    }

    private void writeResults(ConfigurationReportModel data, List<ReportConfiguration> configs) {
        writeConfigurations(configs);
        if (spec.isIncludeRuleSchema()) {
            writeRuleSchema(data.getAttributesWithCompatibilityRules(), data.getAttributesWithDisambiguationRules());
        }
        writeLegend(configs);

    }

    private void writeConfigurations(List<ReportConfiguration> configurations) {
        configurations.forEach(this::writeConfiguration);
    }

    private void writeRuleSchema(List<ReportAttribute> attributesWithCompatibilityRules, List<ReportAttribute> attributesWithDisambiguationRules) {
        final Integer maxC
            = attributesWithCompatibilityRules.stream().map(attr -> attr.getName().length()).max(Integer::compare).orElse(0);
        if (!attributesWithCompatibilityRules.isEmpty()) {
            printHeader(() -> message("Compatibility Rules"));
            writeDescription("The following Attributes have compatibility rules defined.");
            newLine();
            try {
                depth++;
                attributesWithCompatibilityRules.forEach(a -> writeAttribute(maxC, a));
                newLine();
            } finally {
                depth--;
            }
        }

        final Integer maxD = attributesWithDisambiguationRules.stream().map(attr -> attr.getName().length()).max(Integer::compare).orElse(0);
        if (!attributesWithDisambiguationRules.isEmpty()) {
            printHeader(() -> message("Disambiguation Rules"));
            writeDescription("The following Attributes have disambiguation rules defined.");
            newLine();
            try {
                depth++;
                attributesWithDisambiguationRules.forEach(a -> writeAttribute(maxD, a));
                newLine();
            } finally {
                depth--;
            }
        }
    }

    private void writeLegend(List<ReportConfiguration> configs) {
        boolean hasLegacy =  configs.stream().anyMatch(ReportConfiguration::isLegacy);
        boolean hasIncubating = configs.stream().anyMatch(ReportConfiguration::hasIncubatingAttributes) ||
            configs.stream().flatMap(c -> c.getSecondaryVariants().stream()).anyMatch(ReportSecondaryVariant::hasIncubatingAttributes);
        boolean hasVariants = configs.stream().anyMatch(ReportConfiguration::hasVariants);

        output.style(StyledTextOutput.Style.Info);
        if (hasLegacy) {
            output.println("(l) Legacy or deprecated configuration. Those are variants created for backwards compatibility which are both resolvable and consumable.");
        }
        if (hasIncubating) {
            output.text("(i) Configuration uses incubating attributes such as ");
            output.withStyle(StyledTextOutput.Style.Identifier).text("Category.VERIFICATION");
            output.println(".");
        }
        if (recursiveExtensionsPrinted) {
            output.println("(t) Configuration extended transitively.");
        }
        if (hasVariants) {
            output.text("(*) Secondary variants are variants created via the ");
            output.withStyle(StyledTextOutput.Style.Identifier).text("Configuration#getOutgoing(): ConfigurationPublications");
            output.println(" API which also participate in selection, in addition to the configuration itself.");
        }
    }

    private void writeConfiguration(ReportConfiguration config) {
        writeConfigurationNameHeader(config, spec.getReportedTypeAlias());
        writeDescription(config.getDescription());

        if (!config.getAttributes().isEmpty() ||
            (spec.isIncludeCapabilities() && !config.getCapabilities().isEmpty()) ||
            (spec.isIncludeArtifacts() && !config.getArtifacts().isEmpty()) ||
            (spec.isIncludeExtensions() && !config.getExtendedConfigurations().isEmpty()) ||
            (spec.isIncludeVariants() && !config.getSecondaryVariants().isEmpty())) {
            newLine();
        }

        if (spec.isIncludeCapabilities()) {
            writeCapabilities(config.getCapabilities());
        }

        writeAttributes(config.getAttributes());

        if (spec.isIncludeArtifacts()) {
            writeArtifacts(config.getArtifacts());
        }
        if (spec.isIncludeExtensions()) {
            writeExtensions(config.getExtendedConfigurations(), spec.isIncludeExtensionsRecursively());
        }

        if (spec.isIncludeVariants()) {
            writeSecondaryVariants(config.getSecondaryVariants());
        }

        newLine();
    }

    private void writeConfigurationNameHeader(ReportConfiguration config, String targetName) {
        printHeader(() -> {
            output.style(StyledTextOutput.Style.Normal).text(StringUtils.capitalize(targetName) + " ");
            output.style(StyledTextOutput.Style.Header).text(config.getName());
            output.style(StyledTextOutput.Style.Info).println(buildIndicators(config));
        });
    }

    private void writeDescription(String description) {
        indent(false);
        if (description != null) {
            output.style(StyledTextOutput.Style.Normal).println(description);
        }
    }

    private String buildIndicators(ReportConfiguration config) {
        String indicators = "";
        if (config.isLegacy()) {
            indicators += " (l)";
        }
        if (config.hasIncubatingAttributes()) {
            indicators += " (i)";
        }
        return indicators;
    }

    private void writeExtensions(List<ReportConfiguration> extensions, boolean recursive) {
        class FormattedExtension {
            final String name;
            final boolean isTransitive;

            public FormattedExtension(String name, boolean isTransitive) {
                this.name = name;
                this.isTransitive = isTransitive;
            }
        }

        final List<FormattedExtension> extensionsToPrint = extensions.stream()
            .map(e -> new FormattedExtension(e.getName(), false))
            .collect(Collectors.toList());
        if (recursive) {
            int nonRecursiveCount = extensionsToPrint.size();
            extensionsToPrint.addAll(extensions.stream()
                .flatMap(e -> e.getExtendedConfigurations().stream())
                .filter(e -> extensionsToPrint.stream().noneMatch(eToP -> eToP.name.equals(e.getName())))
                .map(e -> new FormattedExtension(e.getName(), true))
                .collect(Collectors.toList()));
            if (nonRecursiveCount != extensionsToPrint.size()) {
                recursiveExtensionsPrinted = true;
            }
        }

        if (!extensions.isEmpty()) {
            printSection("Extended Configurations", () -> {
                extensionsToPrint.stream()
                    .sorted(Comparator.comparing(e -> e.name))
                    .forEach(e -> {
                        indent(true);
                        output.withStyle(StyledTextOutput.Style.Identifier).text(e.name);
                        if (e.isTransitive) {
                            output.withStyle(StyledTextOutput.Style.Info).println(" (t)");
                        } else {
                            newLine();
                        }
                    });
            });
        }
    }

    private void writeSecondaryVariants(List<ReportSecondaryVariant> variants) {
        if (!variants.isEmpty()) {
            newLine();
            printSection("Secondary Variants (*)", () -> {
                variants.forEach(variant -> {
                    newLine();
                    writeSecondaryVariant(variant);
                });
            });
        }
    }

    private void writeAttributes(List<ReportAttribute> attributes) {
        if (!attributes.isEmpty()) {
            Integer max = attributes.stream().map(attr -> attr.getName().length()).max(Integer::compare).orElse(0);
            printSection("Attributes", () -> attributes.forEach(attr -> writeAttribute(max, attr)));
        }
    }

    private void writeAttribute(Integer max, ReportAttribute attr) {
        indent(true);
        if (attr.getValue().isPresent()) {
            valuePair(StringUtils.rightPad(attr.getName(), max), String.valueOf(attr.getValue().orElse("")));
        } else {
            output.style(StyledTextOutput.Style.Identifier).text(attr.getName());
        }
        newLine();
    }

    private void writeArtifacts(List<ReportArtifact> artifacts) {
        if (!artifacts.isEmpty()) {
            printSection("Artifacts", () -> {
                artifacts.forEach(art -> {
                    indent(true);
                    writeArtifact(art);
                    newLine();
                });
            });
        }
    }

    private void writeArtifact(ReportArtifact artifact) {
        String type = artifact.getType();
        String classifier = artifact.getClassifier();
        output.style(StyledTextOutput.Style.Normal).text(artifact.getDisplayName());
        if (StringUtils.isNotEmpty(type)) {
            output.text(" (");
            output.withStyle(StyledTextOutput.Style.Description).text("artifactType");
            output.text(" = ");
            output.withStyle(StyledTextOutput.Style.Identifier).text(type);
            if (StringUtils.isNotEmpty(classifier)) {
                output.text(", ");
                output.withStyle(StyledTextOutput.Style.Description).text("classifier");
                output.text(" = ");
                output.withStyle(StyledTextOutput.Style.Identifier).text(classifier);
            }
            output.text(")");
        }
    }

    private void writeCapabilities(List<ReportCapability> capabilities) {
        if (!capabilities.isEmpty()) {
            class FormattedCapability {
                final String gav;
                final boolean isDefault;

                public FormattedCapability(String name, boolean isDefault) {
                    this.gav = name; this.isDefault = isDefault;
                }
            }

            printSection("Capabilities", () -> {
                capabilities.stream()
                    .map(cap -> new FormattedCapability(cap.toGAV(), cap.isDefault()))
                    .forEach(cap -> {
                        indent(true);
                        output.style(StyledTextOutput.Style.Identifier).text(cap.gav);
                        if (cap.isDefault) {
                            output.style(StyledTextOutput.Style.Normal).text(" (default capability)");
                        }
                        newLine();
                    });
            });
        }
    }

    private String buildIndicators(ReportSecondaryVariant config) {
        String indicators = "";
        if (config.hasIncubatingAttributes()) {
            indicators += " (i)";
        }
        return indicators;
    }

    private void writeSecondaryVariant(ReportSecondaryVariant variant) {
        printHeader(() -> {
            output.style(StyledTextOutput.Style.Normal).text("Secondary Variant ");
            output.withStyle(StyledTextOutput.Style.Header).text(variant.getName());
            output.println(buildIndicators(variant));
        });

        writeDescription(variant.getDescription());

        if (!(variant.getAttributes().isEmpty() && variant.getArtifacts().isEmpty())) {
            newLine();
        }
        writeAttributes(variant.getAttributes());
        writeArtifacts(variant.getArtifacts());
    }

    private void printHeader(Runnable action) {
        output.style(StyledTextOutput.Style.Header);
        indent(false);
        output.println("--------------------------------------------------");
        indent(false);
        action.run();
        output.style(StyledTextOutput.Style.Header);
        indent(false);
        output.println("--------------------------------------------------");
        output.style(StyledTextOutput.Style.Normal);
    }

    private void printSection(String title, Runnable action) {
        printSection(title, null, action);
    }

    private void printSection(String title, @Nullable String description, Runnable action) {
        indent(false);
        output.style(StyledTextOutput.Style.Description).text(title);
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
        output.style(StyledTextOutput.Style.Identifier).text(key);
        output.style(StyledTextOutput.Style.Normal).text(" = " + value);
    }

    private void indent(boolean bullet) {
        output.text(StringUtils.repeat("    ", depth));
        if (depth > 0 && bullet) {
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
