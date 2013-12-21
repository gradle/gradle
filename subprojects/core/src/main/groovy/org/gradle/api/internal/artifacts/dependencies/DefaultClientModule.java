/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;

import java.util.HashSet;
import java.util.Set;

public class DefaultClientModule extends AbstractExternalDependency implements ClientModule {

    private String group;

    private String name;

    private String version;

    private boolean force;

    private Set<ModuleDependency> dependencies = new HashSet<ModuleDependency>();

    public DefaultClientModule(String group, String name, String version) {
        this(group, name, version, null);
    }

    public DefaultClientModule(String group, String name, String version, String configuration) {
        super(configuration);
        if (name == null) {
            throw new InvalidUserDataException("Name must not be null!");
        }
        this.group = group;
        this.name = name;
        this.version = version;
    }

    private String emptyStringIfNull(String value) {
        return value == null ? "" : value;
    }

    public Set<ModuleDependency> getDependencies() {
        return dependencies;
    }

    public String getId() {
        return emptyStringIfNull(group) + ":" + emptyStringIfNull(name) + ":" + emptyStringIfNull(version);
    }

    public String getGroup() {
        return group;
    }

    public ClientModule setGroup(String group) {
        this.group = group;
        return this;
    }

    public String getName() {
        return name;
    }

    public ClientModule setName(String name) {
        this.name = name;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public ClientModule setVersion(String version) {
        this.version = version;
        return this;
    }

    public boolean isForce() {
        return force;
    }

    public ClientModule setForce(boolean force) {
        this.force = force;
        return this;
    }

    public void addDependency(ModuleDependency dependency) {
        this.dependencies.add(dependency);
    }

    public ClientModule copy() {
        DefaultClientModule copiedClientModule = new DefaultClientModule(getGroup(), getName(), getVersion(),
                getConfiguration());
        copyTo(copiedClientModule);
        for (ModuleDependency dependency : dependencies) {
            copiedClientModule.addDependency(dependency.copy());
        }
        return copiedClientModule;
    }

    public boolean contentEquals(Dependency dependency) {
        if (this == dependency) {
            return true;
        }
        if (dependency == null || getClass() != dependency.getClass()) {
            return false;
        }

        ClientModule that = (ClientModule) dependency;
        if (!isContentEqualsFor(that)) {
            return false;
        }

        return dependencies.equals(that.getDependencies());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ClientModule that = (ClientModule) o;
        return isContentEqualsFor(that);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
