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
import java.util.Optional;

public abstract class AbstractConfigurationReportSpec {
    @Nullable private final String searchTarget;
    private final boolean showLegacy;

    public AbstractConfigurationReportSpec(@Nullable String searchTarget, boolean showLegacy) {
        this.searchTarget = searchTarget;
        this.showLegacy = showLegacy;
    }

    public boolean isShowLegacy() {
        return showLegacy;
    }

    public abstract String getReportedTypeAlias();
    public abstract String getFullReportedTypeDesc();
    public abstract String getReportedConfigurationDirection();

    public abstract boolean isIncludeCapabilities();
    public abstract boolean isIncludeArtifacts();
    public abstract boolean isIncludeVariants();
    public abstract boolean isIncludeRuleSchema();

    public Optional<String> getSearchTarget() {
        return Optional.ofNullable(searchTarget);
    }
    public boolean isSearchForSpecificVariant() {
        return null != searchTarget;
    }
}
