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
package org.gradle.internal.component.external.model;

import com.google.common.base.Objects;
import org.gradle.api.capabilities.CapabilityDescriptor;

public class ImmutableCapability implements CapabilityDescriptor {
    private final String group;
    private final String name;
    private final String version;
    private final int hashCode;

    public ImmutableCapability(String group, String name, String version) {
        this.group = group;
        this.name = name;
        this.version = version;
        this.hashCode = Objects.hashCode(group, name, version);
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
        ImmutableCapability that = (ImmutableCapability) o;
        return Objects.equal(group, that.group)
            && Objects.equal(name, that.name)
            && Objects.equal(version, that.version);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "capability "
            + "group='" + group + '\''
            + ", name='" + name + '\''
            + ", version='" + version + '\'';
    }
}
