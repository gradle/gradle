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

package org.gradle.features.internal.builders

import org.gradle.test.fixtures.plugin.PluginBuilder as GradlePluginBuilder

/**
 * Pairs a {@link DefinitionBuilder} with a {@link PluginClassBuilder} to form a complete
 * project type or project feature component.
 *
 * <p>Each {@code projectType {}} or {@code projectFeature {}} block in the test scenario DSL
 * delegates to a DefinitionAndPluginBuilder, which provides access to configure both the definition and
 * the plugin independently.</p>
 *
 * <p>Factory methods {@link #forProjectType} and {@link #forProjectFeature} create instances
 * with sensible defaults that match the standard test fixture shapes (properties, nested types,
 * build models). Tests override only the aspects they care about.</p>
 */
class DefinitionAndPluginBuilder {
    /** The logical name of this component (e.g. "testProjectType", "feature"). */
    String name

    /** The definition builder for this component. */
    DefinitionBuilder definition

    /** The plugin builder for this component. */
    PluginClassBuilder plugin

    /**
     * Configures the definition for this component.
     *
     * @param config closure delegating to {@link DefinitionBuilder}
     */
    void definition(
        @DelegatesTo(value = DefinitionBuilder, strategy = Closure.DELEGATE_FIRST)
        Closure config
    ) {
        config.delegate = definition
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
    }

    /**
     * Configures the definition with an explicit class name override.
     *
     * @param className the definition class name
     * @param config optional closure delegating to {@link DefinitionBuilder}
     */
    void definition(String className,
        @DelegatesTo(value = DefinitionBuilder, strategy = Closure.DELEGATE_FIRST)
        Closure config = {}
    ) {
        definition.className = className
        definition(config)
    }

    /**
     * Configures the plugin for this component.
     *
     * @param config closure delegating to {@link PluginClassBuilder}
     */
    void plugin(
        @DelegatesTo(value = PluginClassBuilder, strategy = Closure.DELEGATE_FIRST)
        Closure config
    ) {
        config.delegate = plugin
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
    }

    /** Whether to suppress plugin generation for this component. */
    boolean suppressPlugin = false

    /** Suppresses plugin class generation for this component. The definition is still generated. */
    void noPlugin() {
        this.suppressPlugin = true
    }

    /** Generates all source files for this component (definition + plugin) and writes them to the plugin builder. */
    void build(GradlePluginBuilder pluginBuilder) {
        definition.build(pluginBuilder)
        if (!suppressPlugin) {
            plugin.build(pluginBuilder)
        }
    }

    /**
     * Creates a DefinitionAndPluginBuilder for a project type with the standard default definition.
     *
     * <p>The default definition matches the standard test fixture shape:</p>
     * <ul>
     *     <li>{@code Property<String> getId()} — top-level property</li>
     *     <li>{@code Foo getFoo()} — nested type with {@code Property<String> getBar()}</li>
     *     <li>{@code FooBuildModel} — build model for Foo with {@code Property<String> getBarProcessed()}</li>
     *     <li>{@code ModelType} — build model with {@code Property<String> getId()}</li>
     * </ul>
     *
     * <p>Naming conventions: name "testProjectType" produces definition class
     * "TestProjectTypeDefinition" and plugin class "TestProjectTypeImplPlugin".</p>
     */
    static DefinitionAndPluginBuilder forProjectType(String name) {
        def definition = new DefinitionBuilder("${capitalize(name)}Definition")
        definition.buildModel("${capitalize(name)}Model")
        def plugin = new PluginClassBuilder()
        plugin.kind = PluginClassBuilder.PluginKind.PROJECT_TYPE
        plugin.pluginClassName = "${capitalize(name)}ImplPlugin"
        plugin.packageName = definition.packageName
        plugin.bindings.add(new BindingDeclaration(definition: definition, name: name))
        return new DefinitionAndPluginBuilder(name: name, definition: definition, plugin: plugin)
    }

    /**
     * Creates a DefinitionAndPluginBuilder for a project feature.
     *
     * <p>The definition has a default empty {@code BuildModel} (named after the feature,
     * e.g. "FeatureModel") but no properties or nested types — callers must declare
     * these explicitly in the {@code definition {}} block.</p>
     *
     * <p>Naming conventions: name "feature" produces definition class "FeatureDefinition"
     * and plugin class "FeatureImplPlugin".</p>
     */
    static DefinitionAndPluginBuilder forProjectFeature(String name) {
        def definition = new DefinitionBuilder("${capitalize(name)}Definition")
        definition.buildModel("${capitalize(name)}Model")
        def plugin = new PluginClassBuilder()
        plugin.kind = PluginClassBuilder.PluginKind.PROJECT_FEATURE
        plugin.pluginClassName = "${capitalize(name)}ImplPlugin"
        plugin.packageName = definition.packageName
        plugin.bindings.add(new BindingDeclaration(
            definition: definition,
            name: name,
            bindingMethodName: "bindProjectFeatureToDefinition"
        ))
        return new DefinitionAndPluginBuilder(name: name, definition: definition, plugin: plugin)
    }

    private static String capitalize(String name) { name[0].toUpperCase() + name[1..-1] }
}
