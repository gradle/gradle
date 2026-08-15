/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.xdcl.ecosystem.plugindev;

import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.xdcl.Reaction;
import org.gradle.api.xdcl.ReactionScope;
import org.gradle.xdcl.ecosystem.support.Repositories;
import org.gradle.xdcl.ecosystem.plugindev.dsl.XdclGradlePlugin;

import java.util.List;

/**
 * Reacts to an {@code xdclGradlePlugin { }} definition by wiring the REAL plugin-development
 * machinery onto the live {@link Project} — unlike the java ecosystem, it registers no model of its
 * own:
 *
 * <ul>
 * <li>{@code java-library} — real compilation and the api/implementation/... configurations;</li>
 * <li>{@code java-gradle-plugin} — the {@code gradlePlugin} extension, descriptor generation, plugin
 *     validation;</li>
 * <li>{@code xdcl-gradle-plugin} — the bundled codegen plugin: generates facades from
 *     {@code src/main/xdcl/*.xdsl} into the main source set, packs the schemas under
 *     {@code META-INF/xdcl/}, and single-sources the {@code gradlePlugin} registration from the
 *     project's {@code <plugin-id>.xdcl} plugin block (role 1).</li>
 * </ul>
 *
 * Declared {@code repositories} are configured through the shared {@link Repositories} helper;
 * declared {@code dependencies} are added to the real configurations {@code java-library} created
 * (which is why {@code DependencyScopes} is not used — it would try to create them again).
 */
public class XdclGradlePluginReaction implements Reaction<XdclGradlePlugin, Project> {

    @Override
    public void on(XdclGradlePlugin data, Project project, ReactionScope scope) {
        project.getPluginManager().apply("java-library");
        project.getPluginManager().apply("java-gradle-plugin");
        project.getPluginManager().apply("xdcl-gradle-plugin");

        Repositories.configure(data, project);
        data.dependencies().ifPresent(dependencies -> {
            addAll(project, "api", dependencies.api());
            addAll(project, "implementation", dependencies.implementation());
            addAll(project, "runtimeOnly", dependencies.runtimeOnly());
            addAll(project, "compileOnly", dependencies.compileOnly());
        });
    }

    private static void addAll(Project project, String configuration, Provider<List<String>> notations) {
        for (String notation : notations.getOrElse(List.of())) {
            project.getDependencies().add(configuration, notation);
        }
    }
}
