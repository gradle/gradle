/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.tasks.diagnostics;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ProjectBackedModule;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A task which reports the outgoing variants of a project on the command line.
 * This is useful for listing what a project produces in terms of variants and
 * what artifacts are attached to each variant.
 * Variants, in this context, must be understood as "things produced by a project
 * which can safely be consumed by another project".
 *
 * @since 6.0
 */
@Incubating
public class OutgoingVariantsReportTask extends DefaultTask {
    private final Property<String> variantSpec = getProject().getObjects().property(String.class);
    private final Property<Boolean> showAll = getProject().getObjects().property(Boolean.class).convention(false);

    @Input
    @Optional
    @Option(option = "variant", description = "The variant name")
    Property<String> getVariantName() {
        return variantSpec;
    }

    @Input
    @Optional
    @Option(option = "all", description = "Shows all variants, including legacy and deprecated configurations")
    Property<Boolean> getShowAll() {
        return showAll;
    }

    @Inject
    protected StyledTextOutputFactory getTextOutputFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    void buildReport() {
        List<Configuration> configurations = filterConfigurations();
        StyledTextOutput output = getTextOutputFactory().create(getClass());
        if (configurations.isEmpty()) {
            reportNoMatchingVariant(configurations, output);
        } else {
            Legend legend = new Legend();
            configurations.forEach(cnf -> reportVariant((ConfigurationInternal) cnf, new ProjectBackedModule((ProjectInternal) getProject()), output, legend));
            legend.print(output);
        }
    }

    private void reportVariant(ConfigurationInternal cnf, ProjectBackedModule projectBackedModule, StyledTextOutput output, Legend legend) {
        // makes sure the configuration is complete before reporting
        cnf.preventFromFurtherMutation();
        Formatter tree = new Formatter(output);
        String name = cnf.getName();
        if (cnf.isCanBeResolved()) {
            name += " (l)";
            legend.hasLegacyConfigurations = true;
        }
        header("Variant " + name, output);
        String description = cnf.getDescription();
        if (description != null) {
            tree.value("Description", description);
            tree.println();
        }
        if (formatAttributesAndCapabilities(cnf, projectBackedModule, tree)) {
            tree.println();
        }
        if (formatArtifacts(cnf.getAllArtifacts(), tree)) {
            tree.println();
        }
        if (formatPublications(cnf, tree, legend)) {
            tree.println();
        }
    }

    private void header(String text, StyledTextOutput output) {
        output.style(StyledTextOutput.Style.Header)
            .println("--------------------------------------------------")
            .println(text)
            .println("--------------------------------------------------")
            .style(StyledTextOutput.Style.Normal);
    }

    private boolean formatPublications(ConfigurationInternal cnf, Formatter tree, Legend legend) {
        NamedDomainObjectContainer<ConfigurationVariant> outgoing = cnf.getOutgoing().getVariants();
        if (!outgoing.isEmpty()) {
            tree.section("Secondary variants (*)", () -> {
                outgoing.forEach(variant -> tree.section("Variant", variant.getName(), () -> {
                    formatAttributes(variant.getAttributes(), tree);
                    formatArtifacts(variant.getArtifacts(), tree);
                }));
                legend.hasPublications = true;
            });
            return true;
        }
        return false;
    }

    private boolean formatArtifacts(PublishArtifactSet artifacts, Formatter tree) {
        if (!artifacts.isEmpty()) {
            tree.section("Artifacts", () -> artifacts.stream()
                .sorted(Comparator.comparing(PublishArtifact::toString))
                .forEach(artifact -> formatArtifact(artifact, tree)));
            return true;
        }
        return false;
    }

    private void formatArtifact(PublishArtifact artifact, Formatter tree) {
        String type = artifact.getType();
        File file = artifact.getFile();
        tree.text(getFileResolver().resolveForDisplay(file));
        if (StringUtils.isNotEmpty(type)) {
            tree.append(" (");
            tree.appendValue("artifactType", type);
            tree.append(")");
        }
        tree.println();
    }

    private void formatAttributes(AttributeContainer attributes, Formatter tree) {
        if (!attributes.isEmpty()) {
            tree.section("Attributes", () -> {
                Integer max = attributes.keySet().stream().map(attr -> attr.getName().length()).max(Integer::compare).get();
                attributes.keySet().stream().sorted(Comparator.comparing(Attribute::getName)).forEach(attr ->
                    tree.value(StringUtils.rightPad(attr.getName(), max), String.valueOf(attributes.getAttribute(attr)))
                );
            });
        }
    }

    private void formatCapabilities(Collection<? extends Capability> capabilities, ProjectBackedModule projectBackedModule, Formatter tree) {
        tree.section("Capabilities", () -> {
            if (capabilities.isEmpty()) {
                tree.text(String.format("%s:%s:%s (default capability)", projectBackedModule.getGroup(), projectBackedModule.getName(), projectBackedModule.getVersion()));
            } else {
                capabilities.forEach(cap -> tree.println(String.format("%s:%s:%s", cap.getGroup(), cap.getName(), cap.getVersion())));
            }
        });
    }

    private boolean formatAttributesAndCapabilities(ConfigurationInternal configuration, ProjectBackedModule projectBackedModule, Formatter tree) {
        AttributeContainerInternal attributes = configuration.getAttributes();
        if (!attributes.isEmpty()) {
            Collection<? extends Capability> capabilities = configuration.getOutgoing().getCapabilities();
            formatCapabilities(capabilities, projectBackedModule, tree);
            tree.println();
            formatAttributes(attributes, tree);
            return true;
        }
        return false;
    }

    private void reportNoMatchingVariant(List<Configuration> configurations, StyledTextOutput output) {
        if (variantSpec.isPresent()) {
            output.println("There is no variant named '" + variantSpec.get() + "' defined on this project.");
            configurations = getAllConsumableConfigurations().collect(Collectors.toList());
        }
        String projectName = getProject().getName();
        if (configurations.isEmpty()) {
            output.println("There are no outgoing variants on project " + projectName);
        } else {
            output.println("Here are the available outgoing variants: " + configurations.stream().map(Configuration::getName).collect(Collectors.joining(", ")));
        }
    }

    private List<Configuration> filterConfigurations() {
        Stream<Configuration> configurations = getAllConsumableConfigurations();
        if (!showAll.get() && !variantSpec.isPresent()) {
            configurations = configurations.filter(files -> !files.isCanBeResolved());
        }
        if (variantSpec.isPresent()) {
            String variantName = variantSpec.get();
            configurations = configurations.filter(cnf -> cnf.getName().equals(variantName));
        }
        return configurations.collect(Collectors.toList());
    }

    private Stream<Configuration> getAllConsumableConfigurations() {
        return getProject().getConfigurations()
            .stream()
            .filter(Configuration::isCanBeConsumed)
            .sorted(Comparator.comparing(Configuration::getName));
    }

    private static class Legend {
        private boolean hasPublications;
        private boolean hasLegacyConfigurations;

        void print(StyledTextOutput output) {
            StyledTextOutput info = output.style(StyledTextOutput.Style.Info);
            if (hasLegacyConfigurations || hasPublications) {
                info.println();
            }
            if (hasLegacyConfigurations) {
                info.println("(l) Legacy or deprecated configuration. Those are variants created for backwards compatibility which are both resolvable and consumable.");
            }
            if (hasPublications) {
                info.text("(*) Secondary variants are variants created via the ")
                    .style(StyledTextOutput.Style.Identifier)
                    .text("Configuration#getOutgoing(): ConfigurationPublications")
                    .style(StyledTextOutput.Style.Info)
                    .text(" API which also participate in selection, in addition to the configuration itself.")
                    .println();
            }
        }
    }

    private static class Formatter {
        private final StyledTextOutput output;
        private int depth;

        private Formatter(StyledTextOutput output) {
            this.output = output;
        }

        void section(String title, Runnable action) {
            section(title, null, action);
        }

        void section(String title, @Nullable String description, Runnable action) {
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

        void value(String key, String value) {
            output.style(StyledTextOutput.Style.Identifier);
            text(key);
            output.style(StyledTextOutput.Style.Normal)
                .println(" = " + value);
        }

        void append(String text) {
            output.text(text);
        }

        void appendValue(String key, String value) {
            output.style(StyledTextOutput.Style.Identifier);
            append(key);
            output.style(StyledTextOutput.Style.Normal)
                .text(" = " + value);
        }

        void text(String text) {
            output.text(StringUtils.repeat("   ", depth));
            if (depth > 0) {
                output.withStyle(StyledTextOutput.Style.Normal).text(" - ");
            }
            output.text(text);
        }

        public void println() {
            output.println();
        }

        public void println(String text) {
            text(text);
            println();
        }
    }
}
