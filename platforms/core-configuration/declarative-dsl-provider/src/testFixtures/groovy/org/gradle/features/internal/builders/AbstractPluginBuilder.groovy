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
 * Base class for plugin source-code builders.
 *
 * <p>Each concrete subclass owns one shape of generated plugin (standalone, single-type,
 * multi-type, single-feature, multi-target-feature, etc.) and renders the corresponding
 * Java or Kotlin source file. The base class collects the state and DSL that is common
 * to every shape, plus the language dispatch that turns a {@link #renderJava()} /
 * {@link #renderKotlin()} pair into a written-to-disk source file.</p>
 */
abstract class AbstractPluginBuilder {
    /** The simple class name of the generated plugin (e.g. "ProjectTypeImplPlugin"). */
    String pluginClassName

    /** The Java package for generated source files. */
    String packageName = "org.gradle.test"

    /** The language to generate source code in. */
    Language language = Language.JAVA

    /** The emission shape of this plugin. See {@link PluginType}. */
    PluginType type = PluginType.WITH_BINDINGS

    /** Modifiers appended to the binding chain (e.g. ".withUnsafeDefinition()"). */
    List<String> bindingModifiers = []

    /** Configuration for the apply action's services and behavior. */
    ApplyActionDeclaration applyActionDeclaration = new ApplyActionDeclaration()

    /** Custom Java code to insert in the apply action body (before task registration). */
    String customApplyActionCode = ""

    // --- Common fluent API ---

    /** Overrides the generated plugin class name. */
    void pluginClassName(String className) { this.pluginClassName = className }

    /** Sets the source code language (Java or Kotlin). */
    void language(Language language) { this.language = language }

    /** Sets the plugin emission shape. See {@link PluginType}. */
    void type(PluginType type) { this.type = type }

    /** Adds {@code .withUnsafeDefinition()} to the binding chain. */
    void unsafeDefinition() { bindingModifiers.add("withUnsafeDefinition()") }

    /** Adds {@code .withUnsafeApplyAction()} to the binding chain. */
    void unsafeApplyAction() { bindingModifiers.add("withUnsafeApplyAction()") }

    /** Sets custom code to execute in the apply action before task registration. */
    void applyActionCode(String code) { this.customApplyActionCode = code }

    /**
     * Configures the apply action's injected services and behavior.
     *
     * @see ApplyActionDeclaration
     */
    void applyAction(
        @DelegatesTo(value = ApplyActionDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config
    ) {
        org.gradle.features.internal.builders.dsl.ClosureConfigure.configure(applyActionDeclaration, config)
    }

    // --- Code generation ---

    /**
     * Generates the plugin source file and writes it to the plugin builder.
     * Produces a single {@code .java} or {@code .kt} file depending on the configured language.
     */
    void build(GradlePluginBuilder pluginBuilder) {
        if (type == PluginType.NO_PLUGIN) {
            throw new IllegalStateException(
                "AbstractPluginBuilder.build() invoked on a NO_PLUGIN plugin; " +
                "the caller should have skipped emission."
            )
        }
        if (language == Language.KOTLIN) {
            pluginBuilder.file("src/main/kotlin/${packageName.replace('.', '/')}/${pluginClassName}.kt") << renderKotlin()
        } else {
            pluginBuilder.file("src/main/java/${packageName.replace('.', '/')}/${pluginClassName}.java") << renderJava()
        }
    }

    /** Renders the Java source for this plugin shape. */
    protected abstract String renderJava()

    /** Renders the Kotlin source for this plugin shape. */
    protected abstract String renderKotlin()
}
