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

import com.google.common.collect.ImmutableList;
import org.gradle.buildinit.specs.internal.BuildInitSpecRegistry;
import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Provides a mechanism for requesting and applying plugins via arguments to a build.
 */
public interface ArgumentSourcedPluginHandler {
    List<String> ARGUMENT_SOURCES_PLUGIN_SUPPLIERS_PROPS = ImmutableList.of(BuildInitSpecRegistry.BUILD_INIT_SPECS_PLUGIN_SUPPLIER);

    /**
     * A static util class responsible for gathering {@link PluginRequest}s added outside any build script.
     * <p>
     * These originate from an id + version pair in the form of {@code id:version}.  These are currently
     * parsed from the system properties in {@link #ARGUMENT_SOURCES_PLUGIN_SUPPLIERS_PROPS}.  This
     * functions as a general-purpose mechanism, where additional properties
     * could be added, each specific to a certain use case.
     * <p>
     * Currently, the only internal process that makes use of this mechanism is the {@code init} task.  But
     * this can be used for any purpose.
     */
    default PluginRequests getArgumentSourcedPlugins() {
        List<PluginRequestInternal> requests = new ArrayList<>();
        ARGUMENT_SOURCES_PLUGIN_SUPPLIERS_PROPS.forEach(property -> {
            String propValue = System.getProperty(property);
            if (propValue != null) {
                String[] pluginRequests = propValue.split(",");
                Arrays.stream(pluginRequests)
                    .map(ArgumentSourcedPluginRequest::parsePluginRequest)
                    .forEach(requests::add);
            }
        });

        return PluginRequests.of(requests);
    }
}
