/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations.conflicts;

import groovy.lang.Closure;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.IvyNode;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.VersionConflictStrategy;
import org.gradle.api.artifacts.VersionConflictStrategyType;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.util.ConfigureUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * by Szczepan Faber, created at: 10/4/11
 */
public class DefaultVersionConflictStrategy implements VersionConflictStrategy {

    private VersionConflictStrategyType type;
    private final DependencyFactory dependencyFactory;

    public DefaultVersionConflictStrategy(DependencyFactory dependencyFactory) {
        this.dependencyFactory = dependencyFactory;
        setType(latest());
    }

    public void setType(VersionConflictStrategyType type) {
        assert type != null : "Cannot set null VersionConflictStrategyType";
        this.type = type;
    }

    public VersionConflictStrategyType getType() {
        return type;
    }

    public VersionConflictStrategyType latest() {
        return new Latest();
    }

    public VersionConflictStrategyType strict(Closure configure) {
        Strict strict = new Strict(dependencyFactory);
        ConfigureUtil.configure(configure, strict);
        return strict;
    }

    public static class Strict implements VersionConflictStrategyType {

        private final DependencyFactory dependencyFactory;
        private Set<Dependency> force = new HashSet<Dependency>();

        public Strict(DependencyFactory dependencyFactory) {
            this.dependencyFactory = dependencyFactory;
        }

        public Strict setForce(Object ... dependencyNotations) {
            for (Object notation : dependencyNotations) {
                Dependency dependency = dependencyFactory.createDependency(notation);
                force.add(dependency);
            }
            return this;
        }

        public Set<Dependency> getForce() {
            return force;
        }

        public IvyNode maybeChooseVersion(IvyNode lhs, IvyNode rhs) {
            for (Dependency d : force) {
                if (matches(lhs.getId(), d)) {
                    return lhs;
                } else if (matches(rhs.getId(), d)) {
                    return rhs;
                }
            }
            return null;
        }

        private boolean matches(ModuleRevisionId id, Dependency dependency) {
            return id.getName().equals(dependency.getName()) &&
                    id.getOrganisation().equals(dependency.getGroup()) &&
                    id.getRevision().equals(dependency.getVersion());
        }
    }

    public static class Latest implements VersionConflictStrategyType {}
}