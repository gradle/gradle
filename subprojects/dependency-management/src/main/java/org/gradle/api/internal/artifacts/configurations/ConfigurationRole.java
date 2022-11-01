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

package org.gradle.api.internal.artifacts.configurations;

import java.util.ArrayList;
import java.util.List;

public enum ConfigurationRole {
    /**
     * An unrestricted configuration, which can be used for any purpose.
     *
     * This is available for backwards compatibility, but should not be used for new configurations.  It is
     * the default role for configurations created when another more specific role is <strong>not</strong> specified.
     */
    @Deprecated
    LEGACY,

    INTENDED_CONSUMABLE,

    INTENDED_RESOLVABLE,

    /**
     * Meant to be used only for declaring dependencies.
     *
     * AKA {@code INTENDED_DECLARABLE}.
     */
    INTENDED_BUCKET,

    DEPRECATED_CONSUMABLE,

    DEPRECATED_RESOLVABLE;

    public static String describeRole(ConfigurationInternal configuration) {
        List<String> descriptions = new ArrayList<>();
        if (configuration.isCanBeConsumed()) {
            descriptions.add("\tConsumable - this configuration can be selected by another project as a dependency" + describeDeprecation(configuration.isDeprecatedForConsumption()));
        }
        if (configuration.isCanBeResolved()) {
            descriptions.add("\tResolvable - this configuration can be resolved by this project to a set of files" + describeDeprecation(configuration.isDeprecatedForResolution()));
        }
        if (configuration.isCanBeDeclaredAgainst()) {
            descriptions.add("\tDeclarable Against - this configuration can have dependencies added to it" + describeDeprecation(configuration.isDeprecatedForDeclarationAgainst()));
        }
        return String.join("\n", descriptions);
    }

    private static String describeDeprecation(boolean deprecated) {
        return deprecated ? " (but this behavior is marked deprecated)" : "";
    }
}
