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
import org.gradle.api.artifacts.component.ComponentSelector;

import java.util.Collection;
import java.util.Iterator;

public class ModuleVersionNotFoundException extends ModuleVersionResolveException {
    /**
     * This is used by {@link ModuleVersionResolveException#withIncomingPaths(java.util.Collection)}.
     */
    @SuppressWarnings("UnusedDeclaration")
    public ModuleVersionNotFoundException(ComponentSelector selector, String message) {
        super(selector, message);
    }

    public ModuleVersionNotFoundException(ModuleVersionSelector selector, String message) {
        super(selector, message);
    }

    public ModuleVersionNotFoundException(ModuleVersionSelector selector, Collection<String> attemptedLocations, Collection<String> unmatchedVersions, Collection<String> rejectedVersions) {
        super(selector, format(selector, attemptedLocations, unmatchedVersions, rejectedVersions));
    }

    public ModuleVersionNotFoundException(ModuleVersionIdentifier id, Collection<String> attemptedLocations) {
        super(id, format(id, attemptedLocations));
    }

    private static String format(ModuleVersionSelector selector, Collection<String> locations, Collection<String> unmatchedVersions, Collection<String> rejectedVersions) {
        StringBuilder builder = new StringBuilder();
        if (unmatchedVersions.isEmpty() && rejectedVersions.isEmpty()) {
            builder.append(String.format("Could not find any matches for %s as no versions of %s:%s are available.", selector, selector.getGroup(), selector.getName()));
        } else {
            builder.append(String.format("Could not find any version that matches %s.", selector));
            if (!unmatchedVersions.isEmpty()) {
                builder.append(String.format("%nVersions that do not match:"));
                appendSizeLimited(builder, unmatchedVersions);
            }
            if (!rejectedVersions.isEmpty()) {
                builder.append(String.format("%nVersions rejected by component selection rules:"));
                appendSizeLimited(builder, rejectedVersions);
            }
        }
        addLocations(builder, locations);
        return builder.toString();
    }

    private static String format(ModuleVersionIdentifier id, Collection<String> locations) {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("Could not find %s.", id));
        addLocations(builder, locations);
        return builder.toString();
    }

    private static void appendSizeLimited(StringBuilder builder, Collection<String> values) {
        Iterator<String> iterator = values.iterator();
        int count = Math.min(5, values.size());
        for (int i = 0; i < count; i++) {
            builder.append(String.format("%n    %s", iterator.next()));
        }
        if (count < values.size()) {
            builder.append(String.format("%n    + %d more", values.size() - count));
        }
    }

    private static void addLocations(StringBuilder builder, Collection<String> locations) {
        if (locations.isEmpty()) {
            return;
        }
        builder.append(String.format("%nSearched in the following locations:"));
        for (String location : locations) {
            builder.append(String.format("%n    %s", location));
        }
    }
}
