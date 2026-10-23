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

package org.gradle.xdl.ecosystem.plugindev;

import org.gradle.api.Project;
import org.gradle.api.xdl.Reaction;
import org.gradle.api.xdl.ReactionScope;
import org.gradle.xdl.ecosystem.plugindev.dsl.XdlGradlePlugin;

/**
 * Reacts to an {@code xdlGradlePlugin { }} definition by wiring the REAL plugin-development
 * machinery onto the live {@link Project} — unlike the java ecosystem, it registers no model of its
 * own:
 *
 * <ul>
 * <li>{@code java-library} — real compilation and the api/implementation/... configurations;</li>
 * <li>{@code java-gradle-plugin} — the {@code gradlePlugin} extension, descriptor generation, plugin
 *     validation;</li>
 * <li>{@code xdl-gradle-plugin} — the bundled codegen plugin: generates facades from
 *     {@code src/main/xdl/*.xdls} into the main source set, packs the schemas under
 *     {@code META-INF/xdl/}, and single-sources the {@code gradlePlugin} registration from the
 *     project's {@code <plugin-id>.xdl} plugin block (role 1).</li>
 * </ul>
 *
 * Declared {@code dependencies} go through {@link DeclaredDependencies}; repositories come from the
 * settings ({@code dependencyResolutionManagement}), not from the root.
 */
public class XdlGradlePluginReaction implements Reaction<XdlGradlePlugin, Project> {

    @Override
    public void on(XdlGradlePlugin data, Project project, ReactionScope scope) {
        project.getPluginManager().apply("java-library");
        project.getPluginManager().apply("java-gradle-plugin");
        project.getPluginManager().apply("xdl-gradle-plugin");

        DeclaredDependencies.configure(data, project);
    }
}
