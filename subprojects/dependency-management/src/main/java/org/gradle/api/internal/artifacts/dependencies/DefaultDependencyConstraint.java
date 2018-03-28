/*
 * Copyright 2017 the original author or authors.
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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.internal.artifacts.ModuleVersionSelectorStrictSpec;

import javax.annotation.Nullable;

public class DefaultDependencyConstraint implements DependencyConstraint {

    public static DefaultDependencyConstraint strictConstraint(String group, String name, String version) {
        return new DefaultDependencyConstraint(group, name, new DefaultMutableVersionConstraint(version, true));
    }

    private final String group;
    private final String name;
    private final MutableVersionConstraint versionConstraint;

    private String reason;

    public DefaultDependencyConstraint(String group, String name, String version) {
        this.group = group;
        this.name = name;
        this.versionConstraint = new DefaultMutableVersionConstraint(version);
    }

    private DefaultDependencyConstraint(String group, String name, MutableVersionConstraint versionConstraint) {
        this.group = group;
        this.name = name;
        this.versionConstraint = versionConstraint;
    }

    @Nullable
    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public String getName() {
        return name;
    }

    @Nullable
    @Override
    public String getVersion() {
        return versionConstraint.getPreferredVersion();
    }

    @Override
    public int hashCode() {
        int result = group != null ? group.hashCode() : 0;
        result = 31 * result + name.hashCode();
        result = 31 * result + versionConstraint.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DependencyConstraint that = (DependencyConstraint) o;
        return contentEquals(that);
    }

    private boolean contentEquals(DependencyConstraint dependency) {
        if (this == dependency) {
            return true;
        }
        if (dependency == null || getClass() != dependency.getClass()) {
            return false;
        }
        DefaultDependencyConstraint that = (DefaultDependencyConstraint) dependency;
        return StringUtils.equals(group, that.getGroup()) && StringUtils.equals(name, that.getName()) && versionConstraint.equals(that.versionConstraint);
    }

    @Override
    public void version(Action<? super MutableVersionConstraint> configureAction) {
        configureAction.execute(versionConstraint);
    }

    @Override
    public VersionConstraint getVersionConstraint() {
        return versionConstraint;
    }

    @Override
    public boolean matchesStrictly(ModuleVersionIdentifier identifier) {
        return new ModuleVersionSelectorStrictSpec(this).isSatisfiedBy(identifier);
    }

    @Override
    public String getReason() {
        return reason;
    }

    @Override
    public void because(String reason) {
        this.reason = reason;
    }

    public DependencyConstraint copy() {
        DefaultDependencyConstraint constraint = new DefaultDependencyConstraint(group, name, versionConstraint);
        constraint.reason = reason;
        return constraint;
    }
}
