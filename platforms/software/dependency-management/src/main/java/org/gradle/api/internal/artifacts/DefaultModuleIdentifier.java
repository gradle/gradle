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

import javax.annotation.Nullable;

public class DefaultModuleIdentifier implements ModuleIdentifier {
    private final String group;
    private final String name;
    private final int hashCode;

    private DefaultModuleIdentifier(@Nullable String group, String name) {
        this.group = group;
        this.name = name;
        this.hashCode = Objects.hashCode(group, name);
    }

    public static ModuleIdentifier newId(ModuleIdentifier other) {
        if (other instanceof DefaultModuleIdentifier) {
            return other;
        }
        return newId(other.getGroup(), other.getName());
    }

    public static ModuleIdentifier newId(@Nullable String group, String name) {
        return new DefaultModuleIdentifier(group, name);
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
    public String toString() {
        return group + ":" + name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultModuleIdentifier that = (DefaultModuleIdentifier) o;
        return hashCode == that.hashCode &&
            Objects.equal(group, that.group) &&
            Objects.equal(name, that.name);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
