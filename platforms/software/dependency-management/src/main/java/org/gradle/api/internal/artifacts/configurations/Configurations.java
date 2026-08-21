/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.capabilities.Capability;

import java.util.Set;

public class Configurations {

    /**
     * Collects the capabilities declared by the given configuration and all configurations
     * in its {@code extendsFrom} hierarchy.
     *
     * <p>Visiting a configuration does not realize its outgoing publications:
     * to declare capability you would need to call {@link Configuration#getOutgoing()}, so no realized publications =  no capabilities.</p>
     */
    public static Set<Capability> collectCapabilities(ConfigurationInternal configuration, Set<Capability> out, Set<Configuration> visited) {
        if (visited.add(configuration)) {
            ConfigurationPublications outgoing = configuration.getOutgoingIfInitialized();
            if (outgoing != null) {
                out.addAll(outgoing.getCapabilities());
            }
            for (Configuration parent : configuration.getExtendsFrom()) {
                collectCapabilities((ConfigurationInternal) parent, out, visited);
            }
        }
        return out;
    }

}
