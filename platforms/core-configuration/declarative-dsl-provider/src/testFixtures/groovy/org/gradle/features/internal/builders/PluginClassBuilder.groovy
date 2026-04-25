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

import org.gradle.features.internal.builders.dsl.ClosureConfigure

/**
 * Generates Java or Kotlin source code for a project type or project feature plugin.
 *
 * <p>The generated plugin class contains:</p>
 * <ul>
 *     <li>A binding annotation ({@code @BindsProjectType} or {@code @BindsProjectFeature})</li>
 *     <li>An inner {@code Binding} class that registers the type/feature with the framework</li>
 *     <li>An inner {@code ApplyAction} class that maps definition properties to the build model
 *         and registers a print task for test verification</li>
 * </ul>
 *
 * <p>The builder derives definition and type information from its {@link #bindings} list.
 * The primary binding ({@code bindings[0]}) provides the definition used for mapping code
 * and display logic. Apply actions are always implemented as classes.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * plugin {
 *     unsafeDefinition()
 *     applyAction {
 *         injectedService "taskRegistrar", TaskRegistrar
 *         eagerlyReadDefinitionValues()
 *     }
 * }
 * </pre>
 *
 * <h2>DSL opt-outs and modifiers (cheat-sheet)</h2>
 *
 * <p>The plugin DSL exposes several one-line toggles that affect how the generated
 * plugin is emitted. They are grouped here by the class on which they are declared
 * so call sites know which nested {@code { }} scope to use.</p>
 *
 * <table>
 *   <caption>DSL opt-outs and modifiers</caption>
 *   <tr><th>DSL call</th><th>Effect</th><th>Declared on</th></tr>
 *   <tr>
 *     <td>{@code type PluginType.X}</td>
 *     <td>Control plugin emission shape: {@code WITH_BINDINGS} (default; emit a
 *         binding-annotated plugin), {@code WITHOUT_BINDINGS} (emit a plain
 *         {@code Plugin<Project>} with no annotation or inner classes), or
 *         {@code NO_PLUGIN} (suppress plugin file entirely; emit only the definition).</td>
 *     <td>{@link PluginClassBuilder} ({@code plugin { }} scope)</td>
 *   </tr>
 *   <tr>
 *     <td>{@code noBuildModel()}</td>
 *     <td>Use {@code BuildModel.None} instead of a real build model; omits the
 *         inner build-model interface on the definition and flips the plugin's
 *         generic argument accordingly. Must be called on <em>both</em> the
 *         definition and the plugin for consistent output.</td>
 *     <td>{@link DefinitionBuilder} and {@link PluginClassBuilder}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code unsafeDefinition()}</td>
 *     <td>Append {@code .withUnsafeDefinition()} to the generated binding chain.</td>
 *     <td>{@link PluginClassBuilder}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code unsafeApplyAction()}</td>
 *     <td>Append {@code .withUnsafeApplyAction()} to the generated binding chain.</td>
 *     <td>{@link PluginClassBuilder}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code bindToBuildModel()}</td>
 *     <td>Use {@code bindProjectFeatureToBuildModel} instead of
 *         {@code bindProjectFeatureToDefinition} for the primary binding
 *         (feature plugins only).</td>
 *     <td>{@link PluginClassBuilder}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code bindingStyle(REIFIED)}</td>
 *     <td>Kotlin only: use reified type-parameter binding. Typically combined
 *         with {@code noBuildModel()}.</td>
 *     <td>{@link PluginClassBuilder}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code applyAction { eagerlyReadDefinitionValues() }}</td>
 *     <td>Read the definition's property values eagerly (at apply time) rather
 *         than via the standard mapping code path.</td>
 *     <td>{@link ApplyActionDeclaration} ({@code applyAction { }} scope)</td>
 *   </tr>
 * </table>
 */
class PluginClassBuilder extends AbstractPluginBuilder {
    /** Whether this plugin binds a project type or a project feature. */
    enum PluginKind {
        /** Generates a plugin with {@code @BindsProjectType}. */
        PROJECT_TYPE,
        /** Generates a plugin with {@code @BindsProjectFeature}. */
        PROJECT_FEATURE
    }

    /** Controls how the binding is expressed in Kotlin code. */
    enum BindingStyle {
        /** Standard class-based binding (used for both Java and Kotlin). */
        CLASS,
        /** Kotlin-only reified type parameter binding (used for no-build-model features). */
        REIFIED
    }

    /** Whether this is a project type or project feature plugin. */
    PluginKind kind = PluginKind.PROJECT_TYPE

    /** The binding style (relevant for Kotlin generation). */
    BindingStyle bindingStyle = BindingStyle.CLASS

    /** Whether the feature plugin has no build model (uses {@code BuildModel.None}). */
    boolean hasNoBuildModel = false

    /** Bindings for this plugin. The first entry is the primary binding. */
    List<BindingDeclaration> bindings = []

    /** Build model implementation classes provided by this plugin (for NDOC element models). */
    List<BuildModelImplDeclaration> buildModelImplementations = []

    /** Returns the primary binding's name, or null if no bindings exist. */
    String getName() {
        bindings ? bindings[0].name : null
    }

    /** Returns the primary binding's definition, or null if no bindings exist. */
    DefinitionBuilder getPrimaryDefinition() {
        bindings ? bindings[0].definition : null
    }

    /** Returns the primary binding's target type class name (feature plugins only). */
    String getBindingTypeClassName() {
        bindings ? bindings[0].bindingTypeClassName : null
    }

    /** Returns the primary binding's method name (feature plugins only). */
    String getBindingMethodName() {
        bindings ? bindings[0].bindingMethodName : "bindProjectFeatureToDefinition"
    }

    // --- Fluent API (subclass-specific) ---

    /** Sets the binding style (CLASS or REIFIED). Only affects Kotlin generation. */
    void bindingStyle(BindingStyle style) { this.bindingStyle = style }

    /** Marks this feature plugin as having no build model (uses {@code BuildModel.None}). */
    void noBuildModel() { this.hasNoBuildModel = true }

    /** Changes the feature binding method to {@code bindProjectFeatureToBuildModel}. */
    void bindToBuildModel() {
        if (bindings) {
            bindings[0].bindingMethodName = "bindProjectFeatureToBuildModel"
        }
    }

    /**
     * Adds a project type binding to this plugin using a component reference.
     * Each binding generates its own ApplyAction class.
     */
    void bindsType(DefinitionAndPluginBuilder type) {
        this.type = PluginType.WITH_BINDINGS
        bindings.add(new BindingDeclaration(definition: type.definition, name: type.name))
    }

    /**
     * Adds a project type binding to this plugin using a definition and name directly.
     * Each binding generates its own ApplyAction class.
     */
    void bindsType(DefinitionBuilder additionalDefinition, String typeName) {
        type = PluginType.WITH_BINDINGS
        bindings.add(new BindingDeclaration(definition: additionalDefinition, name: typeName))
    }

    /**
     * Declares a feature binding target by referencing another component (typically a project type).
     * Resolves to the target's definition class name.
     *
     * @param type the project type component to bind to
     */
    void bindsFeatureTo(DefinitionAndPluginBuilder type) {
        bindsFeatureTo(type.definition.className)
    }

    /**
     * Declares a feature binding target. The first call sets the primary binding's target;
     * subsequent calls add additional binding targets that share the same generic
     * {@code ApplyAction<P>} class.
     */
    void bindsFeatureTo(String className) {
        type = PluginType.WITH_BINDINGS
        if (bindings && bindings[0].bindingTypeClassName == null) {
            bindings[0].bindingTypeClassName = className
        } else {
            bindings.add(new BindingDeclaration(bindingTypeClassName: className))
        }
    }

    /**
     * Declares a build model implementation class for an NDOC element's build model.
     * Generates a static abstract class inside the plugin and adds
     * {@code .withNestedBuildModelImplementationType()} to the binding chain.
     */
    void providesBuildModelImpl(String implClassName, String interfaceName,
        @DelegatesTo(value = BuildModelImplDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config = {}
    ) {
        buildModelImplementations.add(ClosureConfigure.configure(
            new BuildModelImplDeclaration(implClassName: implClassName, interfaceName: interfaceName),
            config
        ))
    }

    // --- Code generation ---

    @Override
    protected String renderJava() {
        return resolveSubclass().renderJava()
    }

    @Override
    protected String renderKotlin() {
        return resolveSubclass().renderKotlin()
    }

    private AbstractPluginBuilder resolveSubclass() {
        if (type == PluginType.WITHOUT_BINDINGS) {
            return asStandalone()
        }
        if (kind == PluginKind.PROJECT_TYPE) {
            return asType()
        }
        return asFeature()
    }

    private StandalonePluginBuilder asStandalone() {
        def b = new StandalonePluginBuilder()
        copyCommonStateTo(b)
        return b
    }

    private AbstractTypePluginBuilder asType() {
        AbstractTypePluginBuilder b
        if (bindings.count { it.definition != null } > 1) {
            b = new MultiTypePluginBuilder()
        } else if (bindingStyle == BindingStyle.REIFIED) {
            b = new ReifiedSingleTypePluginBuilder()
        } else {
            b = new SingleTypePluginBuilder()
        }
        copyCommonStateTo(b)
        b.bindings = bindings
        b.buildModelImplementations = buildModelImplementations
        return b
    }

    private AbstractFeaturePluginBuilder asFeature() {
        AbstractFeaturePluginBuilder b
        if (hasNoBuildModel) {
            b = new NoBuildModelFeaturePluginBuilder()
        } else if (bindings.count { it.bindingTypeClassName != null } > 1) {
            b = new MultiTargetFeaturePluginBuilder()
        } else {
            b = new FeaturePluginBuilder()
        }
        copyCommonStateTo(b)
        b.bindings = bindings
        return b
    }

    private void copyCommonStateTo(AbstractPluginBuilder b) {
        b.pluginClassName = pluginClassName
        b.packageName = packageName
        b.language = language
        b.type = type
        b.bindingModifiers = bindingModifiers
        b.applyActionDeclaration = applyActionDeclaration
        b.customApplyActionCode = customApplyActionCode
    }

}
