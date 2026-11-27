/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.plugins;

import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyConstraintFactory;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.api.provider.Provider;

import javax.inject.Inject;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A plugin that builds a Java platform based on a set of user-declared root dependencies.
 * <p>
 * The resulting platform contains entries for all root dependencies and their transitive
 * dependencies. Conflict resolution occurs when building the platform, so any version conflicts
 * encountered will be resolved, with the results included in the platform.
 */
public abstract class JavaPlatformBuilderPlugin implements Plugin<Project> {

    private final JvmPluginServices jvmPluginServices;
    private final DependencyFactory dependencyFactory;
    private final DependencyConstraintFactory dependencyConstraintFactory;

    @Inject
    public JavaPlatformBuilderPlugin(
        JvmPluginServices jvmPluginServices,
        DependencyFactory dependencyFactory,
        DependencyConstraintFactory dependencyConstraintFactory
    ) {
        this.jvmPluginServices = jvmPluginServices;
        this.dependencyFactory = dependencyFactory;
        this.dependencyConstraintFactory = dependencyConstraintFactory;
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlatformPlugin.class);

        NamedDomainObjectProvider<DependencyScopeConfiguration> platformApi = project.getConfigurations().dependencyScope("platformApi", conf -> {
            conf.setDescription("The declared dependencies to resolve the platform API graph from.");
        });
        @SuppressWarnings("unchecked") // Varargs generic array creation in extendsFrom(Provider...)
        NamedDomainObjectProvider<ResolvableConfiguration> apiClasspath = project.getConfigurations().resolvable("platformApiClasspath", conf -> {
            conf.setDescription("The classpath that resolves the API graph for the platform.");
            conf.extendsFrom(platformApi);
            jvmPluginServices.configureAsCompileClasspath(conf);
        });

        NamedDomainObjectProvider<DependencyScopeConfiguration> platformRuntime = project.getConfigurations().dependencyScope("platformRuntime", conf -> {
            conf.setDescription("The additional declared dependencies to resolve the platform runtime graph from.");
        });
        @SuppressWarnings("unchecked") // Varargs generic array creation in extendsFrom(Provider...)
        NamedDomainObjectProvider<ResolvableConfiguration> runtimeClasspath = project.getConfigurations().resolvable("platformRuntimeClasspath", conf -> {
            conf.setDescription("The classpath that resolves the runtime graph for the platform.");
            conf.extendsFrom(platformApi, platformRuntime);
            conf.shouldResolveConsistentlyWith(apiClasspath.get());
            jvmPluginServices.configureAsRuntimeClasspath(conf);
        });

        // Resolve the platform graphs and add them as dependency constraints to the platform variants.
        project.getConfigurations().named(JavaPlatformPlugin.API_CONFIGURATION_NAME).configure(conf ->
            conf.getDependencyConstraints().addAllLater(getDependencies(apiClasspath))
        );
        project.getConfigurations().named(JavaPlatformPlugin.RUNTIME_CONFIGURATION_NAME).configure(conf ->
            conf.getDependencyConstraints().addAllLater(getDependencies(runtimeClasspath))
        );
    }

    /**
     * Given a configuration, for each component in its resolved graph, return a dependency
     * constraint for that component.
     */
    private Provider<Collection<DependencyConstraint>> getDependencies(NamedDomainObjectProvider<ResolvableConfiguration> graphConfiguration) {
        return graphConfiguration
            .flatMap(conf ->
                conf.getIncoming().getResolutionResult().getRootComponent().zip(
                    conf.getIncoming().getResolutionResult().getRootVariant(),
                    ComponentAndVariant::new
                )
            ).map(root ->
                getComponentIds(root).stream().map(componentId -> {
                    if (componentId instanceof ModuleComponentIdentifier mid) {
                        return dependencyConstraintFactory.create(mid.getGroup(), mid.getModule(), mid.getVersion());
                    } else if (componentId instanceof ProjectComponentIdentifier pid) {
                        return dependencyConstraintFactory.create(dependencyFactory.createProjectDependency(pid.getProjectPath()));
                    } else {
                        throw new GradleException("Unsupported component type '" + componentId.getClass().getName() + "': " + componentId.getDisplayName());
                    }
                }).collect(Collectors.toList())
            );
    }

    /**
     * Walks a dependency graph BFS from the root, returning the IDs of all components
     * present, in the order they were encountered.
     */
    private static Set<ComponentIdentifier> getComponentIds(ComponentAndVariant root) {
        Set<ResolvedVariantResult> seenVariants = new HashSet<>();
        Set<ComponentIdentifier> seenComponents = new LinkedHashSet<>();
        Queue<ComponentAndVariant> queue = new ArrayDeque<>();

        seenVariants.add(root.variant());
        queue.add(root);

        while (!queue.isEmpty()) {
            ComponentAndVariant next = queue.remove();
            seenComponents.add(next.component().getId());

            for (DependencyResult dependency : next.component().getDependenciesForVariant(next.variant())) {
                if (dependency instanceof ResolvedDependencyResult resolved) {
                    ResolvedComponentResult component = resolved.getSelected();
                    ResolvedVariantResult variant = resolved.getResolvedVariant();
                    if (seenVariants.add(variant)) {
                        queue.add(new ComponentAndVariant(component, variant));
                    }
                } else if (dependency instanceof UnresolvedDependencyResult unresolved) {
                    throw new GradleException("Failed to build platform.", unresolved.getFailure());
                }
            }
        }

        // The platform should not constrain itself.
        seenComponents.remove(root.component().getId());
        return Collections.unmodifiableSet(seenComponents);
    }

    /**
     * A variant and the component it belongs to.
     */
    record ComponentAndVariant(
        ResolvedComponentResult component,
        ResolvedVariantResult variant
    ) {}

}
