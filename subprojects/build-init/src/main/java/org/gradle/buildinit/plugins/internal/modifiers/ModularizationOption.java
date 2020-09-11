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

import java.util.ArrayList;
import java.util.List;

public enum ModularizationOption implements WithIdentifier {
    SINGLE_PROJECT("no - only one application project"),
    WITH_LIBRARY_PROJECTS("yes - application and library projects");

    public static ModularizationOption byId(String id) {
        for (ModularizationOption option : values()) {
            if (option.getId().equals(id)) {
                return option;
            }
        }
        return SINGLE_PROJECT;
    }

    public static List<String> listSupported() {
        List<String> result = new ArrayList<>();
        for (ModularizationOption option : values()) {
            result.add(option.getId());
        }
        return result;
    }

    private final String displayName;

    ModularizationOption(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getId() {
        return Names.idFor(this);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
