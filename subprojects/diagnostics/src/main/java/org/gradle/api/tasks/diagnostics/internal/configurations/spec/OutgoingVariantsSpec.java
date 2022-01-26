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
 * An {@link AbstractConfigurationReportSpec} extension that describes an {@link org.gradle.api.tasks.diagnostics.OutgoingVariantsReportTask outgoingVariants} report.
 */
public final class OutgoingVariantsSpec extends AbstractConfigurationReportSpec {
    public OutgoingVariantsSpec(@Nullable String searchTarget, boolean showLegacy) {
        super(searchTarget, showLegacy);
    }

    @Override
    public String getReportedTypeAlias() {
        return "variant";
    }

    @Override
    public String getFullReportedTypeDesc() {
        return "outgoing " + getReportedTypeAlias();
    }

    @Override
    public String getReportedConfigurationDirection() {
        return "consumable";
    }

    @Override
    public boolean isIncludeCapabilities() {
        return true;
    }

    @Override
    public boolean isIncludeArtifacts() {
        return true;
    }

    @Override
    public boolean isIncludeVariants() {
        return true;
    }

    @Override
    public boolean isIncludeRuleSchema() {
        return false;
    }

    @Override
    public boolean isIncludeExtensions() {
        return false;
    }

    @Override
    public boolean isIncludeExtensionsRecursively() {
        return false;
    }

    @Override
    public boolean isPurelyCorrectType(ReportConfiguration configuration) {
        return configuration.getType() == ReportConfiguration.Type.CONSUMABLE;
    }
}
