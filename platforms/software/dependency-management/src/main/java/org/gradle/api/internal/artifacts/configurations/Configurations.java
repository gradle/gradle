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

import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.capabilities.Capability;

import java.util.Collection;
import java.util.Set;

public class Configurations {
    public static ImmutableSet<String> getNames(Collection<Configuration> configurations) {
        if (configurations.isEmpty()) {
            return ImmutableSet.of();
        }
        if (configurations.size() == 1) {
            return ImmutableSet.of(configurations.iterator().next().getName());
        }
        ImmutableSet.Builder<String> names = new ImmutableSet.Builder<>();
        for (Configuration configuration : configurations) {
            names.add(configuration.getName());
        }
        return names.build();
    }

    public static Set<Capability> collectCapabilities(Configuration configuration, Set<Capability> out, Set<Configuration> visited) {
        if (visited.add(configuration)) {
            out.addAll(configuration.getOutgoing().getCapabilities());
            for (Configuration parent : configuration.getExtendsFrom()) {
                collectCapabilities(parent, out, visited);
            }
        }
        return out;
    }

    @Deprecated // TODO:Finalize Upload Removal - Issue #21439
    public static String uploadTaskName(String configurationName) {
        return "upload" + StringUtils.capitalize(configurationName);
    }
}
