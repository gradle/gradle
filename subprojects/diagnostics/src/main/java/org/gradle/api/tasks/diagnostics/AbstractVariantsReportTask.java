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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.diagnostics.internal.VariantsReportFormatter;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Base class for tasks which reports on attributes of a variant or configuration.
 *
 * @since 7.5
 */
@Incubating
@DisableCachingByDefault(because = "Produces only non-cacheable console output")
public abstract class AbstractVariantsReportTask extends DefaultTask {
    protected abstract Predicate<Configuration> configurationsToReportFilter();

    protected abstract String targetName();
    protected abstract String targetTypeDesc();

    protected abstract void reportSingleMatch(ConfigurationInternal configuration, ProjectBackedModule module, StyledTextOutput output, VariantsReportFormatter.Legend legend);

    @Inject
    protected StyledTextOutputFactory getTextOutputFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }

    protected List<Configuration> configurationsToReport() {
        return getConfigurations(configurationsToReportFilter());
    }

    protected List<Configuration> getConfigurations(Predicate<Configuration> filter) {
        return getProject().getConfigurations()
            .stream()
            .filter(filter)
            .sorted(Comparator.comparing(Configuration::getName))
            .collect(Collectors.toList());
    }

    protected void reportNoMatch(Property<String> searchTarget, List<Configuration> configurations, Predicate<Configuration> availableFilter, StyledTextOutput output) {
        if (searchTarget.isPresent()) {
            output.println("There is no " + targetName() + " named '" + searchTarget.get() + "' defined on this project.");
            configurations = getConfigurations(availableFilter); // Reload all available configs
        }

        if (configurations.isEmpty()) {
            output.println("There are no " + targetTypeDesc() + " " + targetName() + "s on project " + getProject().getName());
        } else {
            output.println("Here are the available " + targetTypeDesc() + " " + targetName() + "s: " + configurations.stream().map(Configuration::getName).collect(Collectors.joining(", ")));
        }
    }

    protected void reportMatches(List<Configuration> configurations, StyledTextOutput output) {
        VariantsReportFormatter.Legend legend = new VariantsReportFormatter.Legend();
        configurations.forEach(cnf -> reportSingleMatch((ConfigurationInternal) cnf, new ProjectBackedModule((ProjectInternal) getProject()), output, legend));
        legend.print(output);
    }

    protected void header(String text, StyledTextOutput output) {
        output.style(StyledTextOutput.Style.Header)
            .println("--------------------------------------------------")
            .println(text)
            .println("--------------------------------------------------")
            .style(StyledTextOutput.Style.Normal);
    }

    protected String buildNameWithIndicators(ConfigurationInternal cnf, VariantsReportFormatter.Legend legend) {
        String name = cnf.getName();
        if (cnf.isCanBeResolved() && cnf.isCanBeConsumed()) {
            name += " (l)";
            legend.setHasLegacyConfigurations(true);
        }
        if (cnf.isIncubating()) {
            name += " (i)";
            legend.setHasIncubatingConfigurations(true);
        }
        return name;
    }

    protected boolean formatPublications(ConfigurationInternal cnf, VariantsReportFormatter tree, VariantsReportFormatter.Legend legend) {
        NamedDomainObjectContainer<ConfigurationVariant> outgoing = cnf.getOutgoing().getVariants();
        if (!outgoing.isEmpty()) {
            tree.section("Secondary variants (*)", () -> {
                outgoing.forEach(variant -> tree.section("Variant", variant.getName(), () -> {
                    formatAttributes(variant.getAttributes(), tree);
                    formatArtifacts(variant.getArtifacts(), tree);
                }));
                legend.setHasPublications(true);
            });
            return true;
        }
        return false;
    }

    protected boolean formatArtifacts(PublishArtifactSet artifacts, VariantsReportFormatter tree) {
        if (!artifacts.isEmpty()) {
            tree.section("Artifacts", () -> artifacts.stream()
                .sorted(Comparator.comparing(PublishArtifact::toString))
                .forEach(artifact -> formatArtifact(artifact, tree)));
            return true;
        }
        return false;
    }

    private void formatArtifact(PublishArtifact artifact, VariantsReportFormatter tree) {
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

    protected boolean formatAttributes(ConfigurationInternal configuration, VariantsReportFormatter tree) {
        return formatAttributes(configuration.getAttributes(), tree);
    }

    protected boolean formatAttributes(AttributeContainer attributes, VariantsReportFormatter tree) {
        if (!attributes.isEmpty()) {
            tree.section("Attributes", () -> {
                Integer max = attributes.keySet().stream().map(attr -> attr.getName().length()).max(Integer::compare).get();
                attributes.keySet().stream().sorted(Comparator.comparing(Attribute::getName)).forEach(attr ->
                    tree.value(StringUtils.rightPad(attr.getName(), max), String.valueOf(attributes.getAttribute(attr)))
                );
            });
            return true;
        } else {
            return false;
        }
    }

    protected boolean formatCapabilities(ConfigurationInternal configuration, ProjectBackedModule projectBackedModule, VariantsReportFormatter tree) {
        Collection<? extends Capability> capabilities = configuration.getOutgoing().getCapabilities();
        tree.section("Capabilities", () -> {
            if (capabilities.isEmpty()) {
                tree.text(String.format("%s:%s:%s (default capability)", projectBackedModule.getGroup(), projectBackedModule.getName(), projectBackedModule.getVersion()));
            } else {
                capabilities.forEach(cap -> tree.println(String.format("%s:%s:%s", cap.getGroup(), cap.getName(), cap.getVersion())));
            }
        });
        return true; // Always have at least the default capability
    }
}
