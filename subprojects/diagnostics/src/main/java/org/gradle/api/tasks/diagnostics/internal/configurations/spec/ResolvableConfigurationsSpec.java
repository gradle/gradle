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

import javax.annotation.Nullable;

public class ResolvableConfigurationsSpec extends AbstractConfigurationReportSpec{
    public ResolvableConfigurationsSpec(@Nullable String searchTarget, boolean showLegacy) {
        super(searchTarget, showLegacy);
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
}
