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

package org.gradle.buildinit.plugins.internal.modifiers;

import java.util.Arrays;
import java.util.List;

public enum ComponentType {
    // These are in display order
    APPLICATION("Application", "app", "list", "utilities"),
    LIBRARY("Library", "lib"),
    GRADLE_PLUGIN("Gradle plugin", "plugin"),
    BASIC("Basic (build structure only)");

    private final String displayName;
    private final List<String> defaultProjectNames;

    ComponentType(String displayName, String... defaultProjectNames) {
        this.displayName = displayName;
        this.defaultProjectNames = Arrays.asList(defaultProjectNames);
    }

    public List<String> getDefaultProjectNames() {
        return defaultProjectNames;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return Names.displayNameFor(this);
    }

    public String pluralName() {
        return (toString() + "s").replace("ys", "ies");
    }
}
