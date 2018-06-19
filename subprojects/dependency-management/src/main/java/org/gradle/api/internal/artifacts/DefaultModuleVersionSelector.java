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

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;

public class DefaultModuleVersionSelector implements ModuleVersionSelector {

    private final ModuleIdentifier module;
    private final VersionConstraint moduleVersionConstraint;

    private DefaultModuleVersionSelector(ModuleIdentifier module, VersionConstraint versionConstraint) {
        this.module = module;
        this.moduleVersionConstraint = versionConstraint;
    }

    // DO NOT USE THIS CONSTRUCTOR DIRECTLY
    // It's only there for backwards compatibility with the Nebula plugin
    public DefaultModuleVersionSelector(String group, String name, String version) {
        this(DefaultModuleIdentifier.newId(group, name), new DefaultMutableVersionConstraint(version));
    }

    public String getGroup() {
        return module.getGroup();
    }

    public String getName() {
        return module.getName();
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
    public ModuleIdentifier getModule() {
        return module;
    }

    @Override
    public String toString() {
        return String.format("%s:%s", module, moduleVersionConstraint.getPreferredVersion());
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

        if (module != null ? !module.equals(that.module) : that.module != null) {
            return false;
        }
        if (moduleVersionConstraint != null ? !moduleVersionConstraint.equals(that.moduleVersionConstraint) : that.moduleVersionConstraint != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = module != null ? module.hashCode() : 0;
        result = 31 * result + moduleVersionConstraint.hashCode();
        return result;
    }

    public static ModuleVersionSelector newSelector(ModuleIdentifier module, String preferredVersion) {
        return new DefaultModuleVersionSelector(module, new DefaultMutableVersionConstraint(preferredVersion));
    }

    public static ModuleVersionSelector newSelector(ModuleIdentifier module, VersionConstraint version) {
        return new DefaultModuleVersionSelector(module, version);
    }

    public static ModuleVersionSelector newSelector(ModuleComponentSelector selector) {
        return new DefaultModuleVersionSelector(selector.getModuleIdentifier(), selector.getVersionConstraint());
    }
}
