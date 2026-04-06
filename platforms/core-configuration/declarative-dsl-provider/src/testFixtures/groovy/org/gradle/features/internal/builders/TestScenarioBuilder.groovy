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

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.plugin.PluginBuilder as GradlePluginBuilder

/**
 * Orchestrates a full test scenario by composing project types, project features, and a settings plugin.
 *
 * <p>This is the main DSL entry point (via {@link TestScenarioFixture#testScenario}).
 * It handles the wiring that would otherwise be boilerplate:</p>
 * <ul>
 *     <li>Auto-creates a default project type if features are declared without an explicit type</li>
 *     <li>Propagates the top-level language setting to all components</li>
 *     <li>Auto-registers all plugin classes with the settings plugin</li>
 *     <li>Requires explicit feature binding targets via {@code bindsFeatureTo()}</li>
 *     <li>Assigns plugin IDs to each component</li>
 * </ul>
 *
 * <p>Returns from {@code projectType()} and {@code projectFeature()} can be captured in variables
 * for cross-referencing (e.g. a feature binding to a specific type's build model class).</p>
 */
class TestScenarioBuilder {
    /** The default language for all generated source code. Individual components can override. */
    Language language = Language.JAVA

    /** The declared project types in this scenario. */
    List<DefinitionAndPluginBuilder> types = []

    /** The declared project features in this scenario. */
    List<DefinitionAndPluginBuilder> features = []

    /** Standalone plugins not paired with any definition. */
    List<PluginClassBuilder> standalonePlugins = []

    /** Top-level shared types declared in this scenario, generated as individual Java source files. */
    List<PropertyTypeDeclaration> sharedTypes = []

    /** The settings plugin builder. Auto-populated from declared types and features. */
    SettingsBuilder settings = new SettingsBuilder()

    /** Sets the default language for all generated source code. */
    void language(Language language) { this.language = language }

    /**
     * Declares a project type in this scenario.
     *
     * @param name the project type name (e.g. "testProjectType")
     * @param config optional closure to customize the definition and plugin
     * @return the created DefinitionAndPluginBuilder, for capturing cross-references
     */
    DefinitionAndPluginBuilder projectType(String name,
        @DelegatesTo(value = DefinitionAndPluginBuilder, strategy = Closure.DELEGATE_FIRST)
        Closure config = {}
    ) {
        def type = DefinitionAndPluginBuilder.forProjectType(name)
        config.delegate = type
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        types.add(type)
        return type
    }

    /**
     * Declares a project feature in this scenario.
     *
     * @param name the feature name (e.g. "feature")
     * @param config optional closure to customize the definition and plugin
     * @return the created DefinitionAndPluginBuilder, for capturing cross-references
     */
    DefinitionAndPluginBuilder projectFeature(String name,
        @DelegatesTo(value = DefinitionAndPluginBuilder, strategy = Closure.DELEGATE_FIRST)
        Closure config = {}
    ) {
        def feature = DefinitionAndPluginBuilder.forProjectFeature(name)
        config.delegate = feature
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        features.add(feature)
        return feature
    }

    /**
     * Declares a standalone plugin not paired with any definition.
     *
     * <p>Standalone plugins default to {@code exposesBindings = false}, generating a plain
     * {@code Plugin<Project>} with an empty {@code apply()} method. The closure can override
     * this or add bindings if needed.</p>
     *
     * @param className the plugin class name
     * @param config optional closure to customize the plugin
     * @return the created PluginClassBuilder, for capturing cross-references
     */
    PluginClassBuilder plugin(String className,
        @DelegatesTo(value = PluginClassBuilder, strategy = Closure.DELEGATE_FIRST)
        Closure config = {}
    ) {
        def plugin = new PluginClassBuilder()
        plugin.pluginClassName = className
        plugin.exposesBindings = false
        config.delegate = plugin
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        standalonePlugins.add(plugin)
        return plugin
    }

    /**
     * Declares a shared type at the scenario level.
     *
     * <p>Returns a reference that can be passed to {@code property(name, ref)} on either a
     * {@link DefinitionBuilder} or a {@link BuildModelDeclaration}. The referenced type is
     * rendered once as a top-level Java file via {@link SharedTypeBuilder} rather than
     * duplicated inline at each use site.</p>
     *
     * <p>Allowed inside the closure: {@code property}, {@code listProperty}, sub-nested
     * {@code property(name, String, Closure)}, {@code ndoc}, {@code injectedService},
     * {@code annotations}, {@code implementsDefinition}, and {@code shape}. Disallowed (throws
     * {@link IllegalStateException}): {@code asNdoc}, {@code outProjected},
     * {@code undiscoverable}, and {@code initializeWith} — these are containment-site concerns.</p>
     *
     * @param typeName simple class name of the generated top-level type (e.g. "SourceSet")
     * @param config configuration closure delegating to {@link SharedTypeDsl}
     * @return the declaration, to be captured and passed as a property type
     */
    PropertyTypeDeclaration sharedType(String typeName,
        @DelegatesTo(value = SharedTypeDsl, strategy = Closure.DELEGATE_FIRST)
        Closure config = {}
    ) {
        def declaration = new PropertyTypeDeclaration(typeName: typeName)
        def dsl = new SharedTypeDsl(declaration)
        config.delegate = dsl
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        sharedTypes.add(declaration)
        return declaration
    }

    /**
     * Configures the settings plugin (e.g. to add model defaults).
     *
     * @param config closure delegating to {@link SettingsBuilder}
     */
    void settings(
        @DelegatesTo(value = SettingsBuilder, strategy = Closure.DELEGATE_FIRST)
        Closure config
    ) {
        config.delegate = settings
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
    }

    /**
     * Builds all source files for the scenario and returns the configured PluginBuilder.
     *
     * <p>This method performs auto-wiring (default type creation, language propagation,
     * plugin registration, feature binding resolution) then generates all source files.</p>
     *
     * @param pluginsDir the directory where plugin source files are written
     * @return the PluginBuilder with all source files and plugin IDs registered
     * @throws IllegalStateException if a feature has no binding target and multiple types exist
     */
    GradlePluginBuilder build(TestFile pluginsDir) {
        // Auto-add a default project type if features exist but no types were declared
        if (types.isEmpty() && !features.isEmpty()) {
            types.add(DefinitionAndPluginBuilder.forProjectType("testProjectType"))
        }

        // Propagate top-level language
        [*types, *features].each { comp ->
            if (comp.plugin.language == Language.JAVA) {
                comp.plugin.language = language
            }
        }
        standalonePlugins.each { plugin ->
            if (plugin.language == Language.JAVA) {
                plugin.language = language
            }
        }
        settings.language = language

        // Auto-register plugins with settings (skip suppressed plugins)
        types.findAll { !it.suppressPlugin }.each {
            settings.registersProjectType(it.plugin.pluginClassName)
        }
        features.findAll { !it.suppressPlugin }.each {
            settings.registersProjectFeature(it.plugin.pluginClassName)
        }
        standalonePlugins.each { plugin ->
            if (plugin.exposesBindings) {
                if (plugin.kind == PluginClassBuilder.PluginKind.PROJECT_TYPE) {
                    settings.registersProjectType(plugin.pluginClassName)
                } else {
                    settings.registersProjectFeature(plugin.pluginClassName)
                }
            }
        }

        // Update Kotlin settings plugin class name
        if (language == Language.KOTLIN) {
            settings.pluginClassName = "ProjectFeatureRegistrationPlugin"
        }

        // Build everything
        def pluginBuilder = new GradlePluginBuilder(pluginsDir)
        pluginBuilder.addPluginId("com.example.test-software-ecosystem", settings.pluginClassName)

        types.eachWithIndex { type, i ->
            if (!type.suppressPlugin) {
                def pluginId = i == 0
                    ? "com.example.test-project-type-impl"
                    : "com.example.additional-type-impl-${i}"
                pluginBuilder.addPluginId(pluginId, type.plugin.pluginClassName)
            }
            type.build(pluginBuilder)
        }

        features.eachWithIndex { feature, i ->
            if (!feature.suppressPlugin) {
                def pluginId = i == 0
                    ? "com.example.test-software-feature-impl"
                    : "com.example.additional-feature-impl-${i}"
                pluginBuilder.addPluginId(pluginId, feature.plugin.pluginClassName)
            }
            feature.build(pluginBuilder)
        }

        standalonePlugins.eachWithIndex { plugin, i ->
            def pluginId = "com.example.standalone-plugin-${i}"
            pluginBuilder.addPluginId(pluginId, plugin.pluginClassName)
            plugin.build(pluginBuilder)
        }

        // Build additional definitions referenced by plugin bindings (e.g. from bindsType).
        // Skip the primary binding (index 0) since its definition is already built above.
        [*types, *features].each { comp ->
            comp.plugin.bindings.drop(1).each { binding ->
                if (binding.definition != null) {
                    binding.definition.build(pluginBuilder)
                }
            }
        }

        // Emit shared types as top-level files. Validate uniqueness to catch accidental
        // double-declarations that would otherwise silently overwrite the generated source.
        def duplicates = sharedTypes.groupBy { it.typeName }.findAll { typeName, list -> list.size() > 1 }.keySet()
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("Duplicate sharedType typeName(s): ${duplicates}")
        }
        sharedTypes.each { new SharedTypeBuilder(it).build(pluginBuilder) }

        settings.build(pluginBuilder)
        return pluginBuilder
    }
}
