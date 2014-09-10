/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.resolve;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;

import java.util.List;

public class ModuleVersionNotFoundException extends ModuleVersionResolveException {
    @SuppressWarnings("UnusedDeclaration")
    public ModuleVersionNotFoundException(ModuleVersionSelector selector, String messageFormat) {
        super(selector, messageFormat);
    }

    public ModuleVersionNotFoundException(ModuleVersionSelector selector) {
        super(selector, "Could not find any version that matches %s.");
    }

    public ModuleVersionNotFoundException(ModuleVersionSelector selector, List<String> attemptedLocations) {
        super(selector, format("Could not find any version that matches %s.", attemptedLocations));
    }

    public ModuleVersionNotFoundException(ModuleVersionIdentifier id) {
        super(id, "Could not find %s.");
    }

    public ModuleVersionNotFoundException(ModuleVersionIdentifier id, List<String> attemptedLocations) {
        super(id, format("Could not find %s.", attemptedLocations));
    }

    private static String format(String messageFormat, List<String> locations) {
        if (locations.isEmpty()) {
            return messageFormat;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(messageFormat);
        builder.append(String.format("%nSearched in the following locations:"));
        for (String location : locations) {
            builder.append(String.format("%n    %s", location.replace("%", "%%")));
        }
        return builder.toString();
    }
}
