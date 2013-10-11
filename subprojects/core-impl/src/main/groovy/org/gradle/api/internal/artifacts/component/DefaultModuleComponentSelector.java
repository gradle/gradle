/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.component;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;

public class DefaultModuleComponentSelector implements ModuleComponentSelector {
    private final String displayName;
    private final String group;
    private final String name;
    private final String version;

    public DefaultModuleComponentSelector(String group, String name, String version) {
        this.displayName = "";
        this.group = group;
        this.name = name;
        this.version = version;
    }

    public DefaultModuleComponentSelector(String displayName, String group, String name, String version) {
        this.displayName = displayName;
        this.group = group;
        this.name = name;
        this.version = version;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public boolean matchesStrictly(ModuleComponentIdentifier identifier) {
        return new ModuleComponentSelectorStrictSpec(this).isSatisfiedBy(identifier);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultModuleComponentSelector that = (DefaultModuleComponentSelector) o;

        if (displayName != null ? !displayName.equals(that.displayName) : that.displayName != null) {
            return false;
        }
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
        int result = displayName != null ? displayName.hashCode() : 0;
        result = 31 * result + (group != null ? group.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (version != null ? version.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%s", group, name, version);
    }

    public static ModuleComponentSelector newSelector(String group, String name, String version) {
        return new DefaultModuleComponentSelector(group, name, version);
    }

    public static ModuleComponentSelector newSelector(String displayName, String group, String name, String version) {
        return new DefaultModuleComponentSelector(displayName, group, name, version);
    }
}
