/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.plugin.management.internal.argumentloaded;

import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides a mechanism for requesting plugins via arguments to a build.
 */
public interface ArgumentLoadedPluginHandler {
    String INIT_PROJECT_SPEC_SUPPLIERS_PROP = "org.gradle.internal.buildinit.projectspecs";

    /**
     * A static util class responsible for gathering {@link PluginRequest}s added outside of any build script.
     * <p>
     * These originate from an id + version pair in the form of {@code id:version}.  These are currently
     * parsed from a system property {@link #INIT_PROJECT_SPEC_SUPPLIERS_PROP}.  Other such properties
     * could be added, or this property renamed, to make this a more general-purpose mechanism.  Currently,
     * only the {@code init} task makes use of this mechanism, so the name is appropriate for now.
     */
    default PluginRequests getArgumentLoadedPlugins() {
        String propValue = System.getProperty(INIT_PROJECT_SPEC_SUPPLIERS_PROP);
        if (propValue == null) {
            return PluginRequests.EMPTY;
        } else {
            String[] pluginRequests = propValue.split(",");
            List<PluginRequestInternal> templatePluginRequests = Arrays.stream(pluginRequests)
                .map(ArgumentLoadedPluginRequest::parsePluginRequest)
                .collect(Collectors.toList());
            return PluginRequests.of(templatePluginRequests);
        }
    }
}
