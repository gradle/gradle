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

package org.gradle.plugin.use;

import org.gradle.api.Incubating;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;

/**
 * The DSL for declaring plugins to use in a script.
 * <p>
 * In a build script, the <code>plugins {}</code> script block API is of this type.
 * That is, you can use this API in the body of the plugins script block to declare plugins to be used for the script.
 * </p>
 * <h3>Relationship with the apply() method</h3>
 * <p>
 * The <code>plugins {}</code> block serves a similar purpose to the {@link org.gradle.api.plugins.PluginAware#apply(java.util.Map)} method
 * that can be used to apply a plugin directly to a {@code Project} object or similar.
 * A key difference is that plugins applied via the <code>plugins {}</code> block are conceptually applied to the script, and by extension the script target.
 * At this time there is no observable practical difference between the two approaches with regard to the end result.
 * </p>
 * <h3>Strict Syntax</h3>
 * <p>
 * When used in a build script, the <code>plugins {}</code> block only allows a strict subset of the full build script programming language.
 * Only the API of this type can be used, and values must be literal (e.g. constant strings, not variables).
 * Interpolated strings are permitted for {@link PluginDependencySpec#version(String)}, however replacement values must be sourced from Gradle properties.
 * Moreover, the <code>plugins {}</code> block must be the first code of a build script.
 * There is one exception to this, in that the {@code buildscript {}} block (used for declaring script dependencies) must precede it.
 * </p>
 * <p>
 * This implies the following constraints:
 * </p>
 * <ul>
 * <li>Only {@link #id(String)} method calls may be top level statements</li>
 * <li>{@link #id(String)} calls may only be followed by a {@link PluginDependencySpec#version(String)} and/or {@link PluginDependencySpec#apply(boolean)} method call on the returned object</li>
 * <li>{@link #id(String)}, {@link PluginDependencySpec#version(String)} and {@link PluginDependencySpec#apply(boolean)} methods must be called with a literal argument (i.e. not a variable)</li>
 * <li>The <code>plugins {}</code> script block must follow any <code>buildscript {}</code> script block, but must precede all other logic in the script</li>
 * </ul>
 * <h3>Available Plugins</h3>
 * <h4>Core Plugins</h4>
 * <p>
 * Core Gradle plugins are able to be applied using the <code>plugins {}</code> block.
 * Core plugins must be specified without a version number, and can have a <i>qualified</i> or <i>unqualified</i> id.
 * That is, the {@code java} plugin can be used via:
 * </p>
 * <pre>
 * plugins {
 *   id 'java'
 * }
 * </pre>
 * <p>
 * Or via:
 * </p>
 * <pre>
 * plugins {
 *   id 'org.gradle.java'
 * }
 * </pre>
 * <p>
 * Core Gradle plugins use the {@code org.gradle} namespace.
 * </p>
 * <p>
 * For the list of available core plugins for a particular Gradle version, please consult the user manual.
 * </p>
 * <h4>Community Plugins</h4>
 * <p>
 * Non-core plugins are available from the <a href="http://plugins.gradle.org">Gradle Plugin Portal</a>.
 * These plugins are contributed by users of Gradle to extend Gradle's functionality.
 * Visit <a href="http://plugins.gradle.org">plugins.gradle.org</a> to browse the available plugins and versions.
 * </p>
 * <p>
 * To use a community plugin, the fully qualified id must be specified along with a version.
 * </p>
 * <h3>Settings Script Usage</h3>
 * <p>
 * When used in a settings script, this API sets the default version of a plugin, allowing build scripts to
 * reference a plugin id without an associated version.
 * </p>
 * <p>
 * Within a settings script, the "Strict Syntax" rules outlined above do not apply. The `plugins` block may contain
 * arbitrary code, and version Strings may contain property replacements. It is an error to call the `apply` method
 * with a value other than `false` (the default).
 * </p>
 */
public interface PluginDependenciesSpec {

    /**
     * Add a dependency on the plugin with the given id.
     *
     * <pre>
     * plugins {
     *     id "org.company.myplugin"
     * }
     * </pre>
     * Further constraints (e.g. version number) can be specified by the methods of the return value.
     * <pre>
     * plugins {
     *     id "org.company.myplugin" version "1.3"
     * }
     * </pre>
     *
     * Plugins are automatically applied to the current script by default. This can be disabled using the {@code apply false} option:
     *
     * <pre>
     * plugins {
     *     id "org.company.myplugin" version "1.3" apply false
     * }
     * </pre>
     *
     * This is useful to reuse task classes from a plugin or to apply it to some other target than the current script.
     *
     * @param id the id of the plugin to depend on
     * @return a mutable plugin dependency specification that can be used to further refine the dependency
     */
    PluginDependencySpec id(String id);

    /**
     * Adds a plugin dependency using a notation coming from a version catalog.
     * The resulting dependency spec can be refined with a version overriding
     * what the version catalog provides.
     * @param notation the plugin reference
     * @return a mutable plugin dependency specification  that can be used to further refine the dependency
     *
     * @since 7.2
     */
    @Incubating
    PluginDependencySpec alias(Provider<PluginDependency> notation);

    /**
     * Adds a plugin dependency using a notation coming from a version catalog.
     * The resulting dependency spec can be refined with a version overriding
     * what the version catalog provides.
     * @param notation the plugin reference
     * @return a mutable plugin dependency specification  that can be used to further refine the dependency
     *
     * @since 7.3
     */
    @Incubating
    PluginDependencySpec alias(ProviderConvertible<PluginDependency> notation);

}
