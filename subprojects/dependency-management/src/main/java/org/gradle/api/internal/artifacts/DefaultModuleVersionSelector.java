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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;

public class DefaultModuleVersionSelector implements ModuleVersionSelector {
    private static final Interner<DefaultModuleVersionSelector> INSTANCES_INTERNER = Interners.newStrongInterner();
    private final DefaultModuleIdentifier id;
    private final String version;
    private final int hashCode;
    private String displayName;

    private DefaultModuleVersionSelector(String group, String name, String version) {
        this.id = DefaultModuleIdentifier.of(group, name);
        this.version = version;
        this.hashCode = calculateHashCode();
    }

    public static DefaultModuleVersionSelector of(String group, String name, String version) {
        DefaultModuleVersionSelector instance = new DefaultModuleVersionSelector(group, name, version);
        return INSTANCES_INTERNER.intern(instance);
    }

    public String getGroup() {
        return id.getGroup();
    }

    public String getName() {
        return id.getName();
    }

    public String getVersion() {
        return version;
    }

    public boolean matchesStrictly(ModuleVersionIdentifier identifier) {
        return new ModuleVersionSelectorStrictSpec(this).isSatisfiedBy(identifier);
    }

    @Override
    public String toString() {
        if (displayName == null) {
            displayName = createDisplayName();
        }
        return displayName;
    }

    private String createDisplayName() {
        return String.format("%s:%s:%s", id.getGroup(), id.getName(), version);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultModuleVersionSelector that = (DefaultModuleVersionSelector) o;
        return Objects.equal(id, that.id)
                && Objects.equal(version, that.version);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int calculateHashCode() {
        return Objects.hashCode(id, version);
    }

    public static ModuleVersionSelector newSelector(String group, String name, String version) {
        return of(group, name, version);
    }
}
