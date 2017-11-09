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
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;

public class DefaultModuleVersionSelector implements ModuleVersionSelector {

    private String group;
    private String name;
    private VersionConstraint moduleVersionConstraint;

    private DefaultModuleVersionSelector(String group, String name, VersionConstraint versionConstraint) {
        this.group = group;
        this.name = name;
        this.moduleVersionConstraint = versionConstraint;
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
        return moduleVersionConstraint.getPreferredVersion();
    }

    @Override
    public VersionConstraint getVersionConstraint() {
        return moduleVersionConstraint;
    }

    public boolean matchesStrictly(ModuleVersionIdentifier identifier) {
        return new ModuleVersionSelectorStrictSpec(this).isSatisfiedBy(identifier);
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%s", group, name, moduleVersionConstraint.getPreferredVersion());
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
        if (moduleVersionConstraint != null ? !moduleVersionConstraint.equals(that.moduleVersionConstraint) : that.moduleVersionConstraint != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = group != null ? group.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + moduleVersionConstraint.hashCode();
        return result;
    }

    public static ModuleVersionSelector newSelector(String group, String name, String preferredVersion) {
        return new DefaultModuleVersionSelector(group, name, new DefaultMutableVersionConstraint(preferredVersion));
    }

    public static ModuleVersionSelector newSelector(String group, String name, VersionConstraint version) {
        return new DefaultModuleVersionSelector(group, name, version);
    }

    public static ModuleVersionSelector newSelector(ModuleComponentSelector selector) {
        return new DefaultModuleVersionSelector(selector.getGroup(), selector.getModule(), selector.getVersionConstraint());
    }
}
