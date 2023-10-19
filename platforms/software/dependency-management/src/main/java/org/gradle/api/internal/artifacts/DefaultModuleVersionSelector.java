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

import com.google.common.base.Objects;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;

public class DefaultModuleVersionSelector implements ModuleVersionSelector {

    private final ModuleIdentifier module;
    private final String version;

    private DefaultModuleVersionSelector(ModuleIdentifier module, String versionConstraint) {
        this.module = module;
        this.version = versionConstraint;
    }

    // DO NOT USE THIS CONSTRUCTOR DIRECTLY
    // It's only there for backwards compatibility with the Nebula plugin
    public DefaultModuleVersionSelector(String group, String name, String version) {
        this(DefaultModuleIdentifier.newId(group, name), version);
    }

    @Override
    public String getGroup() {
        return module.getGroup();
    }

    @Override
    public String getName() {
        return module.getName();
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean matchesStrictly(ModuleVersionIdentifier identifier) {
        return new ModuleVersionSelectorStrictSpec(this).isSatisfiedBy(identifier);
    }

    @Override
    public ModuleIdentifier getModule() {
        return module;
    }

    @Override
    public String toString() {
        return String.format("%s:%s", module, version);
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
        return Objects.equal(module, that.module)
            && Objects.equal(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(module, version);
    }

    public static ModuleVersionSelector newSelector(ModuleIdentifier module, String version) {
        return new DefaultModuleVersionSelector(module, version);
    }

    public static ModuleVersionSelector newSelector(ModuleComponentSelector selector) {
        return new DefaultModuleVersionSelector(selector.getModuleIdentifier(), selector.getVersion());
    }
}
