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

import org.gradle.features.annotations.RegistersProjectFeatures
import org.gradle.test.fixtures.plugin.PluginBuilder as GradlePluginBuilder

/**
 * Generates Java or Kotlin source code for a settings plugin that registers project types and features.
 *
 * <p>The generated plugin is annotated with {@code @RegistersProjectFeatures} listing all
 * project type and feature plugin classes. It can optionally configure model defaults in
 * its {@code apply()} method.</p>
 *
 * <p>This builder is typically not configured directly — {@link TestScenarioBuilder} auto-populates
 * it from the declared project types and features. Use the {@link #defaultFor} method to add
 * default property values.</p>
 */
class SettingsBuilder {
    /** The source code language. */
    Language language = Language.JAVA

    /** The settings plugin class name. */
    String pluginClassName = "ProjectTypeRegistrationPlugin"

    /** The Java package for generated source files. */
    String packageName = "org.gradle.test"

    /** Project type plugin class names to register. */
    List<String> projectTypePluginClasses = []

    /** Project feature plugin class names to register. */
    List<String> projectFeaturePluginClasses = []

    /** Model defaults to configure in the settings plugin's apply method. */
    List<DefaultDeclaration> defaults = []

    /** Sets the source code language. */
    void language(Language language) { this.language = language }

    /** Adds a project type plugin class to the {@code @RegistersProjectFeatures} annotation. */
    SettingsBuilder registersProjectType(String pluginClass) {
        projectTypePluginClasses.add(pluginClass)
        return this
    }

    /** Adds a project feature plugin class to the {@code @RegistersProjectFeatures} annotation. */
    SettingsBuilder registersProjectFeature(String pluginClass) {
        projectFeaturePluginClasses.add(pluginClass)
        return this
    }

    /**
     * Adds default property values for a project type. The settings plugin emits
     * {@code settings.getDefaults().add(...)} for each call.
     *
     * @param type the project type component whose definition receives the defaults
     * @param config closure delegating to {@link DefaultDeclaration}
     */
    void defaultFor(DefinitionAndPluginBuilder type,
        @DelegatesTo(value = DefaultDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config
    ) {
        def decl = new DefaultDeclaration(
            typeName: type.name,
            definitionClassName: type.definition.className
        )
        config.delegate = decl
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        defaults.add(decl)
    }

    /**
     * Generates the settings plugin source file and writes it to the plugin builder.
     * Produces a single {@code .java} or {@code .kt} file.
     */
    void build(GradlePluginBuilder pluginBuilder) {
        if (language == Language.KOTLIN) {
            buildKotlin(pluginBuilder)
        } else {
            buildJava(pluginBuilder)
        }
    }

    private void buildJava(GradlePluginBuilder pluginBuilder) {
        def allPlugins = (projectTypePluginClasses + projectFeaturePluginClasses).collect { it + ".class" }.join(", ")

        def defaultsCode = defaults.collect { defaultDeclaration ->
            def conventions = defaultDeclaration.propertyDefaults.collect { propertyName, propertyValue ->
                generateDefaultConvention(propertyName, propertyValue, Language.JAVA)
            }.join("\n")
            """settings.getDefaults().add("${defaultDeclaration.typeName}", ${defaultDeclaration.definitionClassName}.class, definition -> {
                        ${conventions}
                    });"""
        }.join("\n")

        pluginBuilder.file("src/main/java/${packageName.replace('.', '/')}/${pluginClassName}.java") << """
                package ${packageName};

                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.initialization.Settings;
                import org.gradle.api.internal.SettingsInternal;
                import ${RegistersProjectFeatures.class.name};

                @${RegistersProjectFeatures.class.simpleName}({ ${allPlugins} })
                abstract public class ${pluginClassName} implements Plugin<Settings> {
                    @Override
                    public void apply(Settings settings) {
                        ${defaultsCode}
                    }
                }
            """
    }

    private void buildKotlin(GradlePluginBuilder pluginBuilder) {
        def allPlugins = (projectTypePluginClasses + projectFeaturePluginClasses).collect { it + "::class" }.join(", ")

        def defaultsCode = defaults.collect { defaultDeclaration ->
            def conventions = defaultDeclaration.propertyDefaults.collect { propertyName, propertyValue ->
                generateDefaultConvention(propertyName, propertyValue, Language.KOTLIN)
            }.join("\n")
            """settings.defaults.add("${defaultDeclaration.typeName}", ${defaultDeclaration.definitionClassName}::class.java) { definition ->
                        ${conventions}
                    }"""
        }.join("\n")

        pluginBuilder.file("src/main/kotlin/${packageName.replace('.', '/')}/${pluginClassName}.kt") << """
            package ${packageName}

            import org.gradle.api.Plugin
            import org.gradle.api.initialization.Settings
            import ${RegistersProjectFeatures.class.name}

            @${RegistersProjectFeatures.class.simpleName}(${allPlugins})
            class ${pluginClassName} : Plugin<Settings> {
                override fun apply(settings: Settings) {
                    ${defaultsCode}
                }
            }
        """
    }

    private static String generateDefaultConvention(String propertyPath, Object value, Language language = Language.JAVA) {
        def parts = propertyPath.split("\\.")
        if (language == Language.KOTLIN) {
            if (parts.length == 1) {
                return """definition.${parts[0]}.convention("${value}")"""
            }
            def navigation = parts[0..-2].collect { it }.join(".")
            return """definition.${navigation}.${parts[-1]}.convention("${value}")"""
        }
        if (parts.length == 1) {
            return """definition.get${DefinitionBuilder.capitalize(parts[0])}().convention("${value}");"""
        }
        def navigation = parts[0..-2].collect { "get${DefinitionBuilder.capitalize(it)}()" }.join(".")
        return """definition.${navigation}.get${DefinitionBuilder.capitalize(parts[-1])}().convention("${value}");"""
    }

}
