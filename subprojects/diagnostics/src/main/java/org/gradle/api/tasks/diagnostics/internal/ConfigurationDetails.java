/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.AbstractRenderableDependency;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.UnresolvableConfigurationResult2;
import org.gradle.internal.deprecation.DeprecatableConfiguration;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConfigurationDetails {

    public static ConfigurationDetails of(Configuration configuration) {
        boolean canBeResolved = canBeResolved(configuration);
        return new ConfigurationDetails(
            configuration.getName(),
            configuration.getDescription(),
            canBeResolved,
            canBeResolved ? configuration.getIncoming().getResolutionResult().getRootComponent() : null,
            canBeResolved ? null : unresolvableResultOf(configuration)
        );
    }

    private static boolean canBeResolved(Configuration configuration) {
        boolean isDeprecatedForResolving = ((DeprecatableConfiguration) configuration).getResolutionAlternatives() != null;
        return configuration.isCanBeResolved() && !isDeprecatedForResolving;
    }

    private static UnresolvableConfigurationResult2 unresolvableResultOf(Configuration configuration) {
        return new UnresolvableConfigurationResult2(
            configuration.getClass().getName() + configuration.hashCode(),
            configuration.getName(),
            unresolvableChildren(configuration)
        );
    }

    private static Set<? extends RenderableDependency> unresolvableChildren(Configuration configuration) {
        final DependencySet dependencies = configuration.getDependencies();
        if (dependencies.isEmpty()) {
            return Collections.emptySet();
        }
        Set<UnresolvableRenderableDependency> children = Sets.newLinkedHashSet();
        for (final Dependency dependency : dependencies) {
            children.add(new UnresolvableRenderableDependency(
                dependency.getClass().getName() + dependency.hashCode(),
                (dependency instanceof ProjectDependency)
                    ? projectDependencyLabel((ProjectDependency) dependency)
                    : moduleDependencyLabel(dependency)
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

    private final String name;

    @Nullable
    private final String description;

    private final boolean canBeResolved;

    @Nullable
    private final Provider<ResolvedComponentResult> resolutionResultRoot;

    @Nullable
    private final UnresolvableConfigurationResult2 unresolvableResult;

    private ConfigurationDetails(
        String name,
        @Nullable String description,
        boolean canBeResolved,
        @Nullable Provider<ResolvedComponentResult> resolutionResultRoot,
        @Nullable UnresolvableConfigurationResult2 unresolvableResult
    ) {
        this.name = name;
        this.description = description;
        this.canBeResolved = canBeResolved;
        this.resolutionResultRoot = resolutionResultRoot;
        this.unresolvableResult = unresolvableResult;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public boolean isCanBeResolved() {
        return canBeResolved;
    }

    @Nullable
    public Provider<ResolvedComponentResult> getResolutionResultRoot() {
        return resolutionResultRoot;
    }

    @Nullable
    public UnresolvableConfigurationResult2 getUnresolvableResult() {
        return unresolvableResult;
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
