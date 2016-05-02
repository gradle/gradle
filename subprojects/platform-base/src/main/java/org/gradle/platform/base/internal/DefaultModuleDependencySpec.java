/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.platform.base.internal;

import org.apache.commons.lang.ObjectUtils;
import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.Nullable;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.ModuleDependencySpec;
import org.gradle.platform.base.ModuleDependencySpecBuilder;

import static com.google.common.base.Strings.isNullOrEmpty;

public final class DefaultModuleDependencySpec implements ModuleDependencySpec {

    /**
     * Maps an omitted version number to "+", "the latest available version".
     */
    public static String effectiveVersionFor(String version) {
        return isNullOrEmpty(version) ? "+" : version;
    }

    private final String group;
    private final String name;
    private final String version;

    public DefaultModuleDependencySpec(String group, String name, String version) {
        if (group == null || name == null) {
            throw new IllegalDependencyNotation("A module dependency must have at least a group and a module name specified.");
        }
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
    @Nullable
    public String getVersion() {
        return version;
    }

    @Override
    public String getDisplayName() {
        return getGroup() + ":" + getName() + ":" + effectiveVersionFor(getVersion());
    }

   public static class Builder implements ModuleDependencySpecBuilder {
        private String group;
        private String module;
        private String version;

        @Override
        public ModuleDependencySpecBuilder module(String name) {
            if (name != null && name.contains(":")) {
                setValuesFromModuleId(name);
            } else {
                checkNotSet("module", module);
                module = name;
            }
            return this;
        }

       private void checkNotSet(String name, String value) {
           if (value != null) {
               throw new IllegalDependencyNotation(String.format("Cannot set '%s' multiple times for module dependency.", name));
           }
       }

       private void setValuesFromModuleId(String moduleId) {
           String[] components = moduleId.split(":");
           if (components.length < 2 || components.length > 3 || isNullOrEmpty(components[0]) || isNullOrEmpty(components[1])) {
               throw illegalNotation(moduleId);
           }
           group(components[0]).module(components[1]);
           if (components.length > 2) {
               version(components[2]);
           }
       }

       private IllegalDependencyNotation illegalNotation(String moduleId) {
           return new IllegalDependencyNotation(
               String.format(
                   "'%s' is not a valid module dependency notation. Example notations: 'org.gradle:gradle-core:2.2', 'org.mockito:mockito-core'.",
                   moduleId));
       }

        @Override
        public ModuleDependencySpecBuilder group(String name) {
            checkNotSet("group", group);
            group = name;
            return this;
        }

        @Override
        public ModuleDependencySpecBuilder version(String range) {
            checkNotSet("version", version);
            version = range;
            return this;
        }

        @Override
        public DependencySpec build() {
            return new DefaultModuleDependencySpec(group, module, version);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultModuleDependencySpec that = (DefaultModuleDependencySpec) o;
        return ObjectUtils.equals(group, that.group)
            && ObjectUtils.equals(name, that.name)
            && ObjectUtils.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        int result = ObjectUtils.hashCode(group);
        result = 31 * result + ObjectUtils.hashCode(name);
        result = 31 * result + ObjectUtils.hashCode(version);
        return result;
    }
}
