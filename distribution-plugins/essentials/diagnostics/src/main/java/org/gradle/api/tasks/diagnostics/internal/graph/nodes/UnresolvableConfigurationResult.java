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
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ProjectDependency;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UnresolvableConfigurationResult extends AbstractRenderableDependency {
    private final Configuration configuration;

    public UnresolvableConfigurationResult(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Object getId() {
        return configuration;
    }

    @Override
    public String getName() {
        return configuration.getAsPath();
    }

    @Override
    public String getDescription() {
        return configuration.getDescription() + "(n)";
    }

    @Override
    public ResolutionState getResolutionState() {
        return ResolutionState.UNRESOLVED;
    }

    @Override
    public Set<? extends RenderableDependency> getChildren() {
        final DependencySet dependencies = configuration.getDependencies();
        if (dependencies.isEmpty()) {
            return Collections.emptySet();
        }
        Set<RenderableDependency> children = Sets.newLinkedHashSet();
        for (final Dependency dependency : dependencies) {
            children.add(new AbstractRenderableDependency() {
                @Override
                public Object getId() {
                    return dependency;
                }

                @Override
                public String getName() {
                    String label;
                    if (dependency instanceof ProjectDependency) {
                        label = "project " + dependency.getName();
                    } else {
                        label = Joiner.on(":").join(Stream.of(dependency.getGroup(), dependency.getName(), dependency.getVersion()).filter(Objects::nonNull).collect(Collectors.toList()));
                    }
                    return label;
                }

            });
        }
        return children;
    }
}
