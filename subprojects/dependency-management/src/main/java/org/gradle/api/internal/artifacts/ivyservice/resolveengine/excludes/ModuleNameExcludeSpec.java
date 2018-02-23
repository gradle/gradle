/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes;

import org.gradle.api.artifacts.ModuleIdentifier;

/**
 * Excludes any module that has a module name matching the one specified.
 * Does not exclude artifacts.
 */
class ModuleNameExcludeSpec extends AbstractModuleExclusion {
    final String module;

    ModuleNameExcludeSpec(String module) {
        this.module = module;
    }

    @Override
    public String toString() {
        return "{module " + module + "}";
    }

    @Override
    protected boolean doEquals(Object o) {
        ModuleNameExcludeSpec other = (ModuleNameExcludeSpec) o;
        return module.equals(other.module);
    }

    @Override
    protected int doHashCode() {
        return module.hashCode();
    }

    @Override
    public boolean doExcludesSameModulesAs(AbstractModuleExclusion other) {
        ModuleNameExcludeSpec moduleNameExcludeSpec = (ModuleNameExcludeSpec) other;
        return module.equals(moduleNameExcludeSpec.module);
    }

    public boolean excludeModule(ModuleIdentifier element) {
        return element.getName().equals(module);
    }
}
