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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.ProjectBackedModule;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.diagnostics.internal.VariantsReportFormatter;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.work.DisableCachingByDefault;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * A task which reports the outgoing variants of a project on the command line.
 * This is useful for listing what a project produces in terms of variants and
 * what artifacts are attached to each variant.
 * Variants, in this context, must be understood as "things produced by a project
 * which can safely be consumed by another project".
 *
 * @since 6.0
 */
@DisableCachingByDefault(because = "Produces only non-cacheable console output")
public class OutgoingVariantsReportTask extends AbstractVariantsReportTask {
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

    @TaskAction
    void buildReport() {
        StyledTextOutput output = getTextOutputFactory().create(getClass());

        List<Configuration> configurations = configurationsToReport();
        if (configurations.isEmpty()) {
            reportNoMatch(variantSpec, configurations, Configuration::isCanBeConsumed, output);
        } else {
            reportMatches(configurations, output);
        }
    }

    @Override
    protected String targetName() {
        return "variant";
    }

    @Override
    protected String targetTypeDesc() {
        return "outgoing";
    }

    @Override
    protected Predicate<Configuration> configurationsToReportFilter() {
        String variantName = variantSpec.getOrNull();
        return c -> {
            if (!c.isCanBeConsumed()) {
                return false;
            }

            if (showAll.get()) {
                if (variantSpec.isPresent()) {
                    return Objects.equals(variantName, c.getName());
                } else {
                    return true;
                }
            } else {
                if (variantSpec.isPresent()) {
                    return !c.isCanBeResolved() && Objects.equals(variantName, c.getName());
                } else {
                    return !c.isCanBeResolved();
                }
            }
        };
    }

    @Override
    protected void reportSingleMatch(ConfigurationInternal cnf, ProjectBackedModule projectBackedModule, StyledTextOutput output, VariantsReportFormatter.Legend legend) {
        // makes sure the configuration is complete before reporting
        cnf.preventFromFurtherMutation();
        VariantsReportFormatter tree = new VariantsReportFormatter(output);
        String name = buildNameWithIndicators(cnf, legend);
        header(StringUtils.capitalize(targetName()) + " " + name, output);
        String description = cnf.getDescription();
        if (description != null) {
            tree.value("Description", description);
            tree.println();
        }

        // Preserve exsiting behavior where capabilities are not printed if no attributes
        if (!cnf.getAttributes().isEmpty()) {
            if (formatCapabilities(cnf, projectBackedModule, tree)) {
                tree.println();
            }
            if (formatAttributes(cnf, tree)) {
                tree.println();
            }
        }

        if (formatArtifacts(cnf.getAllArtifacts(), tree)) {
            tree.println();
        }
        if (formatPublications(cnf, tree, legend)) {
            tree.println();
        }
    }
}
