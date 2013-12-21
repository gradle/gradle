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
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;

public class DefaultExternalModuleDependency extends AbstractExternalDependency implements ExternalModuleDependency {
    private String group;
    private String name;
    private String version;

    private boolean force;
    private boolean changing;

    public DefaultExternalModuleDependency(String group, String name, String version) {
        this(group, name, version, null);
    }

    public DefaultExternalModuleDependency(String group, String name, String version, String configuration) {
        super(configuration);
        if (name == null) {
            throw new InvalidUserDataException("Name must not be null!");
        }
        this.group = group;
        this.name = name;
        this.version = version;
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public boolean isForce() {
        return force;
    }

    public DefaultExternalModuleDependency setForce(boolean force) {
        this.force = force;
        return this;
    }

    public boolean isChanging() {
        return changing;
    }

    public DefaultExternalModuleDependency setChanging(boolean changing) {
        this.changing = changing;
        return this;
    }

    public DefaultExternalModuleDependency copy() {
        DefaultExternalModuleDependency copiedModuleDependency = new DefaultExternalModuleDependency(getGroup(),
                getName(), getVersion(), getConfiguration());
        copyTo(copiedModuleDependency);
        copiedModuleDependency.setChanging(isChanging());
        return copiedModuleDependency;
    }

    public boolean contentEquals(Dependency dependency) {
        if (this == dependency) {
            return true;
        }
        if (dependency == null || getClass() != dependency.getClass()) {
            return false;
        }

        DefaultExternalModuleDependency that = (DefaultExternalModuleDependency) dependency;
        if (!isContentEqualsFor(that)) {
            return false;
        }

        return changing == that.isChanging();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultExternalModuleDependency that = (DefaultExternalModuleDependency) o;
        return isContentEqualsFor(that);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return "DefaultExternalModuleDependency{" + "group='" + group + '\'' + ", name='" + name + '\'' + ", version='"
                + version + '\'' + ", configuration='" + getConfiguration() + '\'' + '}';
    }
}
