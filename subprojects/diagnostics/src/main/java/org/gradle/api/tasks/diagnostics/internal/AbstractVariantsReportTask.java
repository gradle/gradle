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

package org.gradle.api.tasks.diagnostics.internal;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Internal;
import org.gradle.internal.logging.text.StyledTextOutput;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Base class for tasks which reports on attributes of a variant or configuration.
 *
 * @since 7.5
 */
public abstract class AbstractVariantsReportTask extends DefaultTask {
    @Internal
    protected abstract Predicate<Configuration> getConfigurationsToReportFilter();

    @Internal
    protected List<Configuration> getConfigurationsToReport() {
        return getConfigurations(getConfigurationsToReportFilter());
    }

    @Internal
    protected List<Configuration> getConfigurations(Predicate<Configuration> filter) {
        return getProject().getConfigurations()
            .stream()
            .filter(filter)
            .sorted(Comparator.comparing(Configuration::getName))
            .collect(Collectors.toList());
    }

    protected static class Formatter {
        private final StyledTextOutput output;
        private int depth;

        public Formatter(StyledTextOutput output) {
            this.output = output;
        }

        public void section(String title, Runnable action) {
            section(title, null, action);
        }

        public void section(String title, @Nullable String description, Runnable action) {
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

        public void value(String key, String value) {
            output.style(StyledTextOutput.Style.Identifier);
            text(key);
            output.style(StyledTextOutput.Style.Normal)
                .println(" = " + value);
        }

        public void append(String text) {
            output.text(text);
        }

        public void appendValue(String key, String value) {
            output.style(StyledTextOutput.Style.Identifier);
            append(key);
            output.style(StyledTextOutput.Style.Normal)
                .text(" = " + value);
        }

        public void text(String text) {
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

    protected static class Legend {
        private boolean hasPublications;
        private boolean hasLegacyConfigurations;
        private boolean hasIncubatingConfigurations;

        public Legend() {}

        public boolean hasPublications() {
            return hasPublications;
        }

        public void setHasPublications(boolean hasPublications) {
            this.hasPublications = hasPublications;
        }

        public boolean hasLegacyConfigurations() {
            return hasLegacyConfigurations;
        }

        public void setHasLegacyConfigurations(boolean hasLegacyConfigurations) {
            this.hasLegacyConfigurations = hasLegacyConfigurations;
        }

        public boolean hasIncubatingConfigurations() {
            return hasIncubatingConfigurations;
        }

        public void setHasIncubatingConfigurations(boolean hasIncubatingConfigurations) {
            this.hasIncubatingConfigurations = hasIncubatingConfigurations;
        }

        public void print(StyledTextOutput output) {
            StyledTextOutput info = output.style(StyledTextOutput.Style.Info);
            if (hasLegacyConfigurations || hasPublications) {
                info.println();
            }
            if (hasLegacyConfigurations) {
                info.println("(l) Legacy or deprecated configuration. Those are variants created for backwards compatibility which are both resolvable and consumable.");
            }
            if (hasIncubatingConfigurations) {
                info.println("(i) Configuration uses incubating attributes such as Category.VERIFICATION.");
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
}
