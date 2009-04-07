/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.internal.artifacts.DefaultExcludeRuleContainer;

import java.util.HashSet;

/**
 * @author Hans Dockter
 */
public class Dependencies {
    public static void copy(Dependency source, AbstractDependency target) {
        target.setArtifacts(new HashSet<DependencyArtifact>(source.getArtifacts()));
        target.setDependencyConfiguration(source.getDependencyConfiguration());
        target.setExcludeRuleContainer(new DefaultExcludeRuleContainer(source.getExcludeRules()));
        target.setTransitive(source.isTransitive());
    }

    public static void copyExternal(ExternalDependency source, ExternalDependency target) {
        copy(source, (AbstractDependency) target);
        target.setForce(source.isForce());
    }

    public static boolean isKeyEquals(Dependency dependencyLhs, Dependency dependencyRhs) {
        if (dependencyLhs.getGroup() != null ? !dependencyLhs.getGroup().equals(dependencyRhs.getGroup()) : dependencyRhs.getGroup() != null) return false;
        if (!dependencyLhs.getName().equals(dependencyRhs.getName())) return false;
        if (!dependencyLhs.getDependencyConfiguration().equals(dependencyRhs.getDependencyConfiguration())) return false;
        if (dependencyLhs.getVersion() != null ? !dependencyLhs.getVersion().equals(dependencyRhs.getVersion()) : dependencyRhs.getVersion() != null) return false;
        return true;
    }

    public static boolean isCommonContentEquals(Dependency dependencyLhs, Dependency dependencyRhs) {
        if (!isKeyEquals(dependencyLhs, dependencyRhs)) {
            return false;
        };
        if (dependencyLhs.isTransitive() != dependencyRhs.isTransitive()) return false;
        if (dependencyLhs.getArtifacts() != null ?
                !dependencyLhs.getArtifacts().equals(dependencyRhs.getArtifacts()) : dependencyRhs.getArtifacts() != null) return false;
        if (dependencyLhs.getExcludeRules() != null ?
                !dependencyLhs.getExcludeRules().equals(dependencyRhs.getExcludeRules()) : dependencyRhs.getExcludeRules() != null) return false;
        return true;
    }

    public static boolean isContentEqualsForExternal(ExternalDependency dependencyLhs, ExternalDependency dependencyRhs) {
        if (!isKeyEquals(dependencyLhs, dependencyRhs) || !isCommonContentEquals(dependencyLhs, dependencyRhs)) {
            return false;
        };
        if (dependencyLhs.isForce() != dependencyRhs.isForce()) return false;
        return true;
    }
}
