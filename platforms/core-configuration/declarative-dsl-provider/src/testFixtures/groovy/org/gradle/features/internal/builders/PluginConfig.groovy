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
import org.gradle.test.fixtures.plugin.PluginBuilder as GradlePluginBuilder

/**
 * Collects DSL configuration for a plugin before the concrete {@link AbstractPluginBuilder}
 * subclass is chosen. Each DSL method on the {@code plugin { }} closure populates this record;
 * the concrete builder is instantiated once at {@link #resolve()} time (called from
 * {@link DefinitionAndPluginBuilder#build} or {@link TestScenarioBuilder#build}).
 *
 * <h2>Why this is not a subclass of {@link AbstractPluginBuilder}</h2>
 *
 * <p>The shape-discriminating choice (single-type vs. multi-type vs. reified vs. feature vs.
 * multi-target-feature vs. no-build-model vs. standalone) depends on state that is only known
 * once the user's DSL closure has finished executing — e.g. the count of bindings registered
 * via {@code bindsType()} / {@code bindsFeatureTo()}. A concrete {@code AbstractPluginBuilder}
 * subclass cannot be chosen up-front, so {@code PluginConfig} acts as a staging record that
 * mirrors the common builder state, then dispatches to the correct subclass in
 * {@link #resolve()}. The mirrored fields below intentionally duplicate
 * {@link AbstractPluginBuilder}; the duplication is the cost of late binding.</p>
 *
 * <p>When adding a new common field, update both classes <em>and</em> the copy in
 * {@link #resolve()}.</p>
 *
 * <h2>DSL opt-outs and modifiers (cheat-sheet)</h2>
 *
 * <p>The plugin DSL exposes several one-line toggles that affect how the generated plugin is
 * emitted. They are grouped here so call sites know which nested {@code { }} scope to use.</p>
 *
 * <table>
 *   <caption>DSL opt-outs and modifiers</caption>
 *   <tr><th>DSL call</th><th>Effect</th></tr>
 *   <tr>
 *     <td>{@code type PluginType.X}</td>
 *     <td>Control plugin emission shape: {@code WITH_BINDINGS} (default; emit a
 *         binding-annotated plugin), {@code WITHOUT_BINDINGS} (emit a plain
 *         {@code Plugin<Project>} with no annotation or inner classes), or
 *         {@code NO_PLUGIN} (suppress plugin file entirely; emit only the definition).</td>
 *   </tr>
 *   <tr>
 *     <td>{@code noBuildModel()}</td>
 *     <td>Use {@code BuildModel.None} instead of a real build model; omits the inner build-model
 *         interface on the definition and flips the plugin's generic argument accordingly. Must
 *         be called on <em>both</em> the definition and the plugin for consistent output.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code unsafeDefinition()}</td>
 *     <td>Append {@code .withUnsafeDefinition()} to the generated binding chain.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code unsafeApplyAction()}</td>
 *     <td>Append {@code .withUnsafeApplyAction()} to the generated binding chain.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code bindToBuildModel()}</td>
 *     <td>Use {@code bindProjectFeatureToBuildModel} instead of
 *         {@code bindProjectFeatureToDefinition} for the primary binding (feature plugins only).</td>
 *   </tr>
 *   <tr>
 *     <td>{@code reifiedBinding()}</td>
 *     <td>Kotlin only: emit reified type-parameter binding for a project-type plugin.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code applyAction { eagerlyReadDefinitionValues() }}</td>
 *     <td>Read the definition's property values eagerly (at apply time) rather than via the
 *         standard mapping code path.</td>
 *   </tr>
 * </table>
 */
class PluginConfig {

    // --- Common state ---

    /** The simple class name of the generated plugin (e.g. "ProjectTypeImplPlugin"). */
    String pluginClassName

    /** The Java package for generated source files. */
    String packageName = "org.gradle.test"

    /** The language to generate source code in. {@code null} means inherit from the scenario's top-level setting. */
    Language language = null

    /** The emission shape of this plugin. See {@link PluginType}. */
    PluginType type = PluginType.WITH_BINDINGS

    /** Modifiers appended to the binding chain (e.g. ".withUnsafeDefinition()"). */
    List<String> bindingModifiers = []

    /** Configuration for the apply action's services and behavior. */
    ApplyActionDeclaration applyActionDeclaration = new ApplyActionDeclaration()

    /** Custom code to insert in the apply action body (before task registration). */
    String customApplyActionCode = ""

    // --- Discriminator state ---

    /** Whether this is a project type or project feature plugin. */
    PluginKind kind = PluginKind.PROJECT_TYPE

    /** Whether the feature plugin has no build model (uses {@code BuildModel.None}). */
    boolean hasNoBuildModel = false

    /** Whether the project-type plugin uses the Kotlin reified binding form. */
    boolean reifiedStyle = false

    /** Bindings for this plugin. The first entry is the primary binding. */
    List<BindingDeclaration> bindings = []

    /** Build model implementation classes provided by this plugin (for NDOC element models). */
    List<BuildModelImplDeclaration> buildModelImplementations = []

    /** Returns the primary binding's name, or null if no bindings exist. */
    String getName() { bindings ? bindings[0].name : null }

    /** Returns the primary binding's definition, or null if no bindings exist. */
    DefinitionBuilder getPrimaryDefinition() { bindings ? bindings[0].definition : null }

    /** Returns the primary binding's target type class name (feature plugins only). */
    String getBindingTypeClassName() { bindings ? bindings[0].bindingTypeClassName : null }

    /** Returns the primary binding's method name (feature plugins only). */
    String getBindingMethodName() {
        bindings ? bindings[0].bindingMethodName : "bindProjectFeatureToDefinition"
    }

    // --- Common fluent API ---

    /** Overrides the generated plugin class name. */
    void pluginClassName(String className) { this.pluginClassName = className }

    /** Sets the source code language explicitly (Java or Kotlin). Pass {@code null} to inherit from the scenario top-level. */
    void language(Language language) { this.language = language }

    /** Sets the plugin emission shape. See {@link PluginType}. */
    void type(PluginType type) { this.type = type }

    /** Adds {@code .withUnsafeDefinition()} to the binding chain. */
    void unsafeDefinition() { bindingModifiers.add("withUnsafeDefinition()") }

    /** Adds {@code .withUnsafeApplyAction()} to the binding chain. */
    void unsafeApplyAction() { bindingModifiers.add("withUnsafeApplyAction()") }

    /** Sets custom code to execute in the apply action before task registration. */
    void applyActionCode(String code) { this.customApplyActionCode = code }

    // --- Subclass-specific fluent API ---

    /** Marks this feature plugin as having no build model (uses {@code BuildModel.None}). */
    void noBuildModel() { this.hasNoBuildModel = true }

    /** Selects Kotlin reified-style binding for a project-type plugin. */
    void reifiedBinding() { this.reifiedStyle = true }

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

    /** Configures the apply action's injected services and behavior. */
    void applyAction(
        @DelegatesTo(value = ApplyActionDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config
    ) {
        ClosureConfigure.configure(applyActionDeclaration, config)
    }

    // --- Resolution & build ---

    /**
     * Selects the concrete {@link AbstractPluginBuilder} subclass that will render this plugin
     * and copies the collected DSL state onto it. Mirrored fields kept in sync by hand —
     * see the class-level Javadoc for why this is not subclassed.
     */
    AbstractPluginBuilder resolve() {
        AbstractPluginBuilder b
        if (type == PluginType.WITHOUT_BINDINGS) {
            b = new StandalonePluginBuilder()
        } else if (kind == PluginKind.PROJECT_TYPE) {
            b = resolveTypeBuilder()
        } else {
            b = resolveFeatureBuilder()
        }
        b.pluginClassName = pluginClassName
        b.packageName = packageName
        b.language = language ?: Language.JAVA
        b.type = type
        b.bindingModifiers = bindingModifiers
        b.applyActionDeclaration = applyActionDeclaration
        b.customApplyActionCode = customApplyActionCode
        return b
    }

    /** Generates the plugin source file and writes it to the plugin builder. */
    void build(GradlePluginBuilder pluginBuilder) {
        resolve().build(pluginBuilder)
    }

    private AbstractTypePluginBuilder resolveTypeBuilder() {
        AbstractTypePluginBuilder b
        if (bindings.count { it.definition != null } > 1) {
            b = new MultiTypePluginBuilder()
        } else if (reifiedStyle) {
            b = new ReifiedSingleTypePluginBuilder()
        } else {
            b = new SingleTypePluginBuilder()
        }
        b.bindings = bindings
        b.buildModelImplementations = buildModelImplementations
        return b
    }

    private AbstractFeaturePluginBuilder resolveFeatureBuilder() {
        AbstractFeaturePluginBuilder b
        if (hasNoBuildModel) {
            b = new NoBuildModelFeaturePluginBuilder()
        } else if (bindings.count { it.bindingTypeClassName != null } > 1) {
            b = new MultiTargetFeaturePluginBuilder()
        } else {
            b = new FeaturePluginBuilder()
        }
        b.bindings = bindings
        return b
    }
}
