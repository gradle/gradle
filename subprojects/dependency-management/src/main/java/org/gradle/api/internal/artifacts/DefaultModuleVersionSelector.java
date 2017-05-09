/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;

public class DefaultModuleVersionSelector implements ModuleVersionSelector {

    private String group;
    private String name;
    private String version;

    public DefaultModuleVersionSelector(String group, String name, String version) {
        this.group = group;
        this.name = name;
        this.version = version;
    }

    public String getGroup() {
        return group;
    }

    public DefaultModuleVersionSelector setGroup(String group) {
        this.group = group;
        return this;
    }

    public String getName() {
        return name;
    }

    public DefaultModuleVersionSelector setName(String name) {
        this.name = name;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public boolean matchesStrictly(ModuleVersionIdentifier identifier) {
        return new ModuleVersionSelectorStrictSpec(this).isSatisfiedBy(identifier);
    }

    public DefaultModuleVersionSelector setVersion(String version) {
        this.version = version;
        return this;
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%s", group, name, version);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultModuleVersionSelector)) {
            return false;
        }

        DefaultModuleVersionSelector that = (DefaultModuleVersionSelector) o;

        if (group != null ? !group.equals(that.group) : that.group != null) {
            return false;
        }
        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (version != null ? !version.equals(that.version) : that.version != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = group != null ? group.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (version != null ? version.hashCode() : 0);
        return result;
    }

    public static ModuleVersionSelector newSelector(String group, String name, String version) {
        return new DefaultModuleVersionSelector(group, name, version);
    }
}
