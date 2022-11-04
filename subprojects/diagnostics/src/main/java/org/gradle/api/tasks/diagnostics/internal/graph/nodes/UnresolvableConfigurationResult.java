/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.tasks.diagnostics.internal.graph.nodes;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.DependencyConstraintSet;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.SelfResolvingDependency;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UnresolvableConfigurationResult extends AbstractRenderableDependency {

    public static UnresolvableConfigurationResult of(Configuration configuration) {
        return new UnresolvableConfigurationResult(
            configuration.getClass().getName() + configuration.hashCode(),
            configuration.getName(),
            unresolvableChildren(configuration)
        );
    }

    private static Set<? extends RenderableDependency> unresolvableChildren(Configuration configuration) {
        Set<UnresolvableRenderableDependency> children = Sets.newLinkedHashSet();
        DependencySet dependencies = configuration.isCanBeResolved() ? configuration.getAllDependencies() : configuration.getDependencies();
        for (final Dependency dependency : dependencies) {
            children.add(new UnresolvableRenderableDependency(
                dependency.getClass().getName() + dependency.hashCode(),
                dependency instanceof ProjectDependency ? projectDependencyLabel((ProjectDependency) dependency) :
                dependency instanceof SelfResolvingDependency ? "files(...)" : moduleDependencyLabel(dependency))
            );
        }
        DependencyConstraintSet dependencyConstraints = configuration.isCanBeResolved() ? configuration.getAllDependencyConstraints() : configuration.getDependencyConstraints();
        for (final DependencyConstraint dependencyConstraint : dependencyConstraints) {
            children.add(new UnresolvableRenderableDependency(
                    dependencyConstraint.getClass().getName() + dependencyConstraint.hashCode(),
                    constraintLabel(dependencyConstraint)
            ));
        }
        return children;
    }

    private static String projectDependencyLabel(ProjectDependency dependency) {
        return "project " + dependency.getName();
    }

    private static String moduleDependencyLabel(Dependency dependency) {
        return Joiner.on(":").join(
            Stream.of(dependency.getGroup(), dependency.getName(), dependency.getVersion())
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
        );
    }

    private static String constraintLabel(DependencyConstraint dependency) {
        return Joiner.on(":").join(
                Stream.of(dependency.getGroup(), dependency.getName(), dependency.getVersion())
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList())
        );
    }

    private final Object id;
    private final String name;
    private final Set<? extends RenderableDependency> children;

    private UnresolvableConfigurationResult(
        Object id,
        String name,
        Set<? extends RenderableDependency> children
    ) {
        this.id = id;
        this.name = name;
        this.children = children;
    }

    @Override
    public Object getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ResolutionState getResolutionState() {
        return ResolutionState.DECLARED;
    }

    @Override
    public Set<? extends RenderableDependency> getChildren() {
        return children;
    }

    private static final class UnresolvableRenderableDependency extends AbstractRenderableDependency {

        private final Object id;
        private final String name;

        public UnresolvableRenderableDependency(Object id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public Object getId() {
            return id;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
