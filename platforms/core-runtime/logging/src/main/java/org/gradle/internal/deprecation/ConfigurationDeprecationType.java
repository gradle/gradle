/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.deprecation;

import java.util.Locale;

public enum ConfigurationDeprecationType {
    DEPENDENCY_DECLARATION("use", true),
    CONSUMPTION("use attributes to consume", false),
    RESOLUTION("resolve", true),
    ARTIFACT_DECLARATION("use", true);

    public final String usage;
    public final boolean inUserCode;

    ConfigurationDeprecationType(String usage, boolean inUserCode) {
        this.usage = usage;
        this.inUserCode = inUserCode;
    }

    public String displayName() {
        return name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
