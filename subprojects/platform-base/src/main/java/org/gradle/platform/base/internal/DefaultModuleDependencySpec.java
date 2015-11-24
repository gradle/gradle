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
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.ModuleDependencySpec;
import org.gradle.platform.base.ModuleDependencySpecBuilder;

public final class DefaultModuleDependencySpec implements ModuleDependencySpec {

    private final String group;
    private final String name;
    private final String version;

    public DefaultModuleDependencySpec(String group, String name, String version) {
        if (group == null || name == null) {
            throw new IllegalArgumentException("A module dependency spec must have at least a group and a module name.");
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
    public String getVersion() {
        return version;
    }

    @Override
    public String getDisplayName() {
        return String.format("%s:%s:%s", optional(getGroup()), optional(getName()), optional(getVersion()));
    }

    private String optional(String s) {
        return s != null ? s : "*";
    }

   public static class Builder implements ModuleDependencySpecBuilder {
        private String group;
        private String module;
        private String version;

        @Override
        public ModuleDependencySpecBuilder module(String name) {
            module = name;
            return this;
        }

        @Override
        public ModuleDependencySpecBuilder group(String name) {
            group = name;
            return this;
        }

        @Override
        public ModuleDependencySpecBuilder version(String range) {
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
