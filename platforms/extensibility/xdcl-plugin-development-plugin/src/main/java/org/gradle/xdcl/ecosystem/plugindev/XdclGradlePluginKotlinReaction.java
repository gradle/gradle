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

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.api.xdcl.Reaction;
import org.gradle.api.xdcl.ReactionScope;
import org.gradle.xdcl.ecosystem.plugindev.dsl.XdclGradlePluginKotlin;
import org.gradle.xdcl.ecosystem.support.Repositories;

import java.util.List;

/**
 * {@link XdclGradlePluginReaction} for a plugin whose reactions are written in Kotlin: reacts to an
 * {@code xdclGradlePluginKotlin { }} definition with the same wiring plus a Kotlin toolchain.
 *
 * <p>The toolchain is not part of the distribution, so the settings must have put one on the build
 * classpath (settings-classpath plugins are applicable by id from any project — the project plugin
 * registry chains to the settings scope). Whichever is present is applied, the distribution-matched
 * embedded toolchain first:
 *
 * <ul>
 * <li>{@code org.gradle.kotlin.embedded-kotlin} — declared as {@code { id "embedded-kotlin",
 *     apply false }}; the XDCL front end desugars the alias, defaulting the version to the one the
 *     running distribution expects;</li>
 * <li>{@code org.jetbrains.kotlin.jvm} — declared with its full id and an explicit version, for a
 *     build that wants a specific Kotlin.</li>
 * </ul>
 *
 * <p>Neither toolchain configures repositories, so the template's declared {@code repositories}
 * (e.g. {@code [:mavenCentral]}) must cover the Kotlin artifacts.
 */
public class XdclGradlePluginKotlinReaction implements Reaction<XdclGradlePluginKotlin, Project> {

    /** In preference order; the first one on the classpath wins. */
    private static final List<String> KOTLIN_TOOLCHAIN_PLUGIN_IDS = List.of(
        "org.gradle.kotlin.embedded-kotlin",
        "org.jetbrains.kotlin.jvm"
    );

    @Override
    public void on(XdclGradlePluginKotlin data, Project project, ReactionScope scope) {
        // A java plugin must precede the Kotlin toolchain: EmbeddedKotlinPlugin wires the
        // compileOnly/testImplementation configurations the java plugins create.
        project.getPluginManager().apply("java-library");
        project.getPluginManager().apply("java-gradle-plugin");
        applyKotlinToolchain(project);
        project.getPluginManager().apply("xdcl-gradle-plugin");

        Repositories.configure(data, project);
        DeclaredDependencies.configure(data, project);
    }

    private static void applyKotlinToolchain(Project project) {
        // Plain apply-and-catch: the provider locks the project's classloader scope before
        // dispatching reactions, so a registry miss surfaces as UnknownPluginException exactly as
        // it would from a build-script body.
        for (String pluginId : KOTLIN_TOOLCHAIN_PLUGIN_IDS) {
            try {
                project.getPluginManager().apply(pluginId);
                return;
            } catch (UnknownPluginException e) {
                // not on the build classpath — try the next toolchain
            }
        }
        throw new GradleException(
            "xdclGradlePluginKotlin requires a Kotlin toolchain on the build classpath, but neither "
                + "'org.gradle.kotlin.embedded-kotlin' nor 'org.jetbrains.kotlin.jvm' was found. "
                + "Declare one in the settings plugins list:\n"
                + "  plugins [\n"
                + "    { id \"embedded-kotlin\", apply false }\n"
                + "  ]\n"
                + "or, for a specific Kotlin version:\n"
                + "  plugins [\n"
                + "    { id \"org.jetbrains.kotlin.jvm\", version \"<kotlinVersion>\", apply false }\n"
                + "  ]");
    }
}
