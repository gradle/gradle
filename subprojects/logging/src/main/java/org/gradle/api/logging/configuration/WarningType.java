/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.logging.configuration;

import org.gradle.api.Incubating;

/**
 * Specifies the warning type a user wants to see
 *
 * @since 4.5
 */
@Incubating
public enum WarningType {
    /**
     * Show all warnings (e.g. deprecation warnings)
     */
    All("all"),

    /**
     * Default value. By default, all deprecation warnings will be suppressed and a single summary message will be displayed at the end of a build indicating how many deprecation warnings suppressed.
     */
    Summary("summary"),

    /**
     * No deprecation warnings at all.
     */
    NoDeprecation("no-deprecation");

    private String buildOption;

    WarningType(String buildOption) {
        this.buildOption = buildOption;
    }

    public String getBuildOption() {
        return this.buildOption;
    }

    public static WarningType fromBuildOption(String value) {
        for (WarningType warningType : values()) {
            if (warningType.buildOption.equalsIgnoreCase(value)) {
                return warningType;
            }
        }
        throw new IllegalArgumentException("No enum constant WarningType." + value);
    }
}
