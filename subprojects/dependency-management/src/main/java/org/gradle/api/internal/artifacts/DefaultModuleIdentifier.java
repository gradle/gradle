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
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.gradle.api.artifacts.ModuleIdentifier;

public class DefaultModuleIdentifier implements ModuleIdentifier {
    private static final Interner<DefaultModuleIdentifier> INSTANCES_INTERNER = Interners.newStrongInterner();
    private final String group;
    private final String name;
    private final int hashCode;
    private String displayName;

    private DefaultModuleIdentifier(String group, String name) {
        assert group != null : "group cannot be null";
        assert name != null : "name cannot be null";
        this.group = group;
        this.name = name;
        this.hashCode = calculateHashCode();
    }

    public static ModuleIdentifier newId(String group, String name) {
        return of(group, name);
    }

    public static DefaultModuleIdentifier of(String group, String name) {
        DefaultModuleIdentifier instance = new DefaultModuleIdentifier(group, name);
        return INSTANCES_INTERNER.intern(instance);
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        if (displayName == null) {
            displayName = createDisplayName();
        }
        return displayName;
    }

    private String createDisplayName() {
        return String.format("%s:%s", group, name);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        DefaultModuleIdentifier other = (DefaultModuleIdentifier) obj;
        if (hashCode() != other.hashCode()) {
            return false;
        }
        if (!group.equals(other.group)) {
            return false;
        }
        if (!name.equals(other.name)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int calculateHashCode() {
        return Objects.hashCode(group, name);
    }
}
