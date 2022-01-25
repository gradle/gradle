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

package org.gradle.api.tasks.diagnostics.internal.configurations.spec;

import org.gradle.api.tasks.diagnostics.internal.configurations.model.ReportConfiguration;

import javax.annotation.Nullable;

/**
 * An {@link AbstractConfigurationReportSpec} extension that describes a {@link org.gradle.api.tasks.diagnostics.ResolvableConfigurationsReportTask resolvableConfigurations}
 * report.
 */
public final class ResolvableConfigurationsSpec extends AbstractConfigurationReportSpec {
    private final boolean recursive;

    public ResolvableConfigurationsSpec(@Nullable String searchTarget, boolean showLegacy, boolean recursive) {
        super(searchTarget, showLegacy);
        this.recursive = recursive;
    }

    @Override
    public String getReportedTypeAlias() {
        return "configuration";
    }

    @Override
    public String getFullReportedTypeDesc() {
        return getReportedConfigurationDirection() + " " + getReportedTypeAlias();
    }

    @Override
    public String getReportedConfigurationDirection() {
        return "resolvable";
    }

    @Override
    public boolean isIncludeCapabilities() {
        return false;
    }

    @Override
    public boolean isIncludeArtifacts() {
        return false;
    }

    @Override
    public boolean isIncludeVariants() {
        return false;
    }

    @Override
    public boolean isIncludeRuleSchema() {
        return true;
    }

    @Override
    public boolean isIncludeExtensions() {
        return true;
    }

    @Override
    public boolean isIncludeExtensionsRecursively() {
        return recursive;
    }

    @Override
    public boolean isPurelyCorrectType(ReportConfiguration configuration) {
        return configuration.getType() == ReportConfiguration.Type.RESOLVABLE;
    }
}
