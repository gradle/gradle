/*
 * Copyright 2007-2008 the original author or authors.
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

import groovy.lang.GString;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.util.WrapUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Hans Dockter
 */
public class DefaultModuleDependency extends AbstractDependency implements ModuleDependency {
    private String group;
    private String name;
    private String version;

    private boolean force = false;
    private boolean changing = false;
    private boolean transitive = true;

    public DefaultModuleDependency(String group, String name, String version) {
        if (name == null) {
            throw new InvalidUserDataException("Name must not be null!");
        }
        this.group = group;
        this.name = name;
        this.version = version;
    }

    public DefaultModuleDependency force(boolean force) {
        this.force = force;
        return this;
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

    public boolean isTransitive() {
        return transitive;
    }

    public DefaultModuleDependency setTransitive(boolean transitive) {
        this.transitive = transitive;
        return this;
    }

    public boolean isForce() {
        return force;
    }

    public DefaultModuleDependency setForce(boolean force) {
        this.force = force;
        return this;
    }

    public boolean isChanging() {
        return changing;
    }

    public DefaultModuleDependency setChanging(boolean changing) {
        this.changing = changing;
        return this;
    }

    public DefaultModuleDependency copy() {
        DefaultModuleDependency copiedModuleDependency = new DefaultModuleDependency(getGroup(), getName(), getVersion());
        Dependencies.copyExternal(this, copiedModuleDependency);
        copiedModuleDependency.setChanging(isChanging());
        return copiedModuleDependency;
    }

    public boolean contentEquals(Dependency dependency) {
        if (this == dependency) return true;
        if (dependency == null || getClass() != dependency.getClass()) return false;

        DefaultModuleDependency that = (DefaultModuleDependency) dependency;
        if (!Dependencies.isContentEqualsForExternal(this, that)) return false;

        return changing == that.isChanging();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultModuleDependency that = (DefaultModuleDependency) o;

        return Dependencies.isKeyEquals(this, that);
    }

    @Override
    public String toString() {
        return "DefaultModuleDependency{" +
                "group='" + group + '\'' +
                ", name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", dependencyConfiguration" + getDependencyConfiguration() + '\'' +
                '}';
    }
}
