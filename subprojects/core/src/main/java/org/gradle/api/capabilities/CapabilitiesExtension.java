/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.capabilities;

import com.google.common.base.Objects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.internal.metaobject.MethodAccess;
import org.gradle.internal.metaobject.MethodMixIn;

import java.util.Collection;

/**
 * The project extension responsible for managing the capabilities of a component.
 *
 * @since 4.7
 */
@Incubating
public class CapabilitiesExtension implements MethodMixIn {
    private final ConfigurationContainer configurations;
    private final MethodAccess dynamicMethods;

    private final Multimap<Configuration, CapabilityDescriptor> descriptors = ArrayListMultimap.create();

    public CapabilitiesExtension(ConfigurationContainer configurations) {
        this.configurations = configurations;
        this.dynamicMethods = new CapabilitiesExtensionMethodAccess();
    }

    public CapabilityDescriptor add(Configuration configuration, String notation) {
        String[] parts = validateNotation(notation);
        return add(configuration, parts[0], parts[1], parts[2]);
    }

    public CapabilityDescriptor add(String configurationName, String group, String name, String version) {
        return add(configurations.getByName(configurationName), group, name, version);
    }

    public CapabilityDescriptor add(Configuration configuration, String group, String name, String version) {
        if (!configurations.contains(configuration)) {
            throw new InvalidUserDataException("Currently you can only declare capabilities on configurations from the same project.");
        }
        Collection<CapabilityDescriptor> current = descriptors.get(configuration);
        checkNoDuplicate(group, name, version, current);
        LocalComponentCapability capability = new LocalComponentCapability(group, name, version);
        descriptors.put(configuration, capability);
        return capability;
    }

    private void checkNoDuplicate(String group, String name, String version, Collection<CapabilityDescriptor> current) {
        for (CapabilityDescriptor descriptor : current) {
            if (descriptor.getGroup().equals(group) && descriptor.getName().equals(name) && !descriptor.getVersion().equals(version)) {
                throw new InvalidUserDataException("Cannot add capability " + group + ":" + name + " with version " + version + " because it's already defined with version " + descriptor.getVersion());
            }
        }
    }

    public Collection<? extends CapabilityDescriptor> getCapabilities(Configuration configuration) {
        return descriptors.get(configuration);
    }

    @Override
    public MethodAccess getAdditionalMethods() {
        return dynamicMethods;
    }

    private static String[] validateNotation(String notation) {
        String[] parts = notation.split(":");
        if (parts.length != 3) {
            throw new InvalidUserDataException("Invalid capability notation: '" + notation + "'. Capabilities notation consists of group, name, version separated by semicolons. For example: 'org.mycompany:capability:1.0'.");
        }
        return parts;
    }

    private static class LocalComponentCapability implements CapabilityDescriptor {
        private final String group;
        private final String name;
        private final String version;

        public LocalComponentCapability(String group, String name, String version) {
            this.group = group;
            this.name = name;
            this.version = version;
        }

        @Override
        public String getGroup() {
            return group;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            LocalComponentCapability that = (LocalComponentCapability) o;
            return Objects.equal(group, that.group)
                && Objects.equal(name, that.name)
                && Objects.equal(version, that.version);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(group, name, version);
        }

        @Override
        public String toString() {
            return "capability "
                + "group='" + group + '\''
                + ", name='" + name + '\''
                + ", version='" + version + '\'';
        }
    }

    private class CapabilitiesExtensionMethodAccess implements MethodAccess {
        public boolean hasMethod(String name, Object... arguments) {
            return arguments.length != 0 && configurations.findByName(name) != null;
        }

        @Override
        public DynamicInvokeResult tryInvokeMethod(String name, Object... arguments) {
            if (arguments.length != 1) {
                return DynamicInvokeResult.notFound();
            }
            Configuration configuration = configurations.findByName(name);
            if (configuration == null) {
                return DynamicInvokeResult.notFound();
            }
            Object argument = arguments[0];
            if (argument instanceof CharSequence) {
                return DynamicInvokeResult.found(add(configuration, argument.toString()));
            }
            return DynamicInvokeResult.notFound();
        }
    }
}
