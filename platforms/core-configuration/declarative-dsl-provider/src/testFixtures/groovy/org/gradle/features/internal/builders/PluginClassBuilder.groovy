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

import org.gradle.api.provider.ProviderFactory
import org.gradle.features.annotations.BindsProjectFeature
import org.gradle.features.annotations.BindsProjectType
import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.Definition
import org.gradle.features.binding.ProjectFeatureApplicationContext
import org.gradle.features.binding.ProjectFeatureApplyAction
import org.gradle.features.binding.ProjectFeatureBinding
import org.gradle.features.binding.ProjectFeatureBindingBuilder
import org.gradle.features.binding.ProjectTypeApplyAction
import org.gradle.features.binding.ProjectTypeBinding
import org.gradle.features.binding.ProjectTypeBindingBuilder
import org.gradle.features.file.ProjectFeatureLayout
import org.gradle.features.internal.builders.dsl.ClosureConfigure
import org.gradle.features.registration.TaskRegistrar
import org.gradle.test.fixtures.plugin.PluginBuilder as GradlePluginBuilder

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
 *     <td>{@code noPlugin()}</td>
 *     <td>Suppress plugin file entirely; only the definition is emitted.</td>
 *     <td>{@link DefinitionAndPluginBuilder} (component scope)</td>
 *   </tr>
 *   <tr>
 *     <td>{@code noBindings()}</td>
 *     <td>Emit a plain {@code Plugin<Project>} with no {@code @BindsProjectType} /
 *         {@code @BindsProjectFeature} annotation and no inner classes.</td>
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
class PluginClassBuilder {
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

    /** The simple class name of the generated plugin (e.g. "ProjectTypeImplPlugin"). */
    String pluginClassName

    /** The Java package for generated source files. */
    String packageName = "org.gradle.test"

    /** The language to generate source code in. */
    Language language = Language.JAVA

    /** The binding style (relevant for Kotlin generation). */
    BindingStyle bindingStyle = BindingStyle.CLASS

    /** Modifiers appended to the binding chain (e.g. ".withUnsafeDefinition()"). */
    List<String> bindingModifiers = []

    /** Whether this plugin exposes bindings. If false, generates a plain plugin with no binding annotation. */
    boolean exposesBindings = true

    /** Whether the feature plugin has no build model (uses {@code BuildModel.None}). */
    boolean hasNoBuildModel = false

    /** Bindings for this plugin. The first entry is the primary binding. */
    List<BindingDeclaration> bindings = []

    /** Build model implementation classes provided by this plugin (for NDOC element models). */
    List<BuildModelImplDeclaration> buildModelImplementations = []

    /** Configuration for the apply action's services and behavior. */
    ApplyActionDeclaration applyActionDeclaration = new ApplyActionDeclaration()

    /** Custom Java code to insert in the apply action body (before task registration). */
    String customApplyActionCode = ""

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

    // --- Fluent API ---

    /** Overrides the generated plugin class name. */
    void pluginClassName(String className) { this.pluginClassName = className }

    /** Sets the source code language (Java or Kotlin). */
    void language(Language language) { this.language = language }

    /** Sets the binding style (CLASS or REIFIED). Only affects Kotlin generation. */
    void bindingStyle(BindingStyle style) { this.bindingStyle = style }

    /** Adds {@code .withUnsafeDefinition()} to the binding chain. */
    void unsafeDefinition() { bindingModifiers.add("withUnsafeDefinition()") }

    /** Adds {@code .withUnsafeApplyAction()} to the binding chain. */
    void unsafeApplyAction() { bindingModifiers.add("withUnsafeApplyAction()") }

    /** Generates a plain plugin with no binding annotation or inner classes. */
    void noBindings() { this.exposesBindings = false }

    /** Marks this feature plugin as having no build model (uses {@code BuildModel.None}). */
    void noBuildModel() { this.hasNoBuildModel = true }

    /** Sets custom Java code to execute in the apply action before task registration. */
    void applyActionCode(String code) { this.customApplyActionCode = code }

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
        exposesBindings = true
        bindings.add(new BindingDeclaration(definition: type.definition, name: type.name))
    }

    /**
     * Adds a project type binding to this plugin using a definition and name directly.
     * Each binding generates its own ApplyAction class.
     */
    void bindsType(DefinitionBuilder additionalDefinition, String typeName) {
        exposesBindings = true
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
        exposesBindings = true
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

    /**
     * Configures the apply action's injected services and behavior.
     *
     * @see ApplyActionDeclaration
     */
    void applyAction(
        @DelegatesTo(value = ApplyActionDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config
    ) {
        ClosureConfigure.configure(applyActionDeclaration, config)
    }

    // --- Code generation ---

    /**
     * Generates the plugin source file and writes it to the plugin builder.
     * Produces a single {@code .java} or {@code .kt} file depending on the configured language.
     */
    void build(GradlePluginBuilder pluginBuilder) {
        if (language == Language.KOTLIN) {
            pluginBuilder.file("src/main/kotlin/${packageName.replace('.', '/')}/${pluginClassName}.kt") << getKotlinClassContent()
        } else {
            pluginBuilder.file("src/main/java/${packageName.replace('.', '/')}/${pluginClassName}.java") << getJavaClassContent()
        }
    }

    // --- Java code generation ---

    private String getJavaClassContent() {
        if (!exposesBindings) {
            return generateNoBindingsPlugin()
        }
        if (kind == PluginKind.PROJECT_TYPE) {
            return generateJavaProjectTypePlugin()
        }
        return generateJavaProjectFeaturePlugin()
    }

    private String generateNoBindingsPlugin() {
        return """
            package ${packageName};

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            abstract public class ${pluginClassName} implements Plugin<Project> {
                @Override
                public void apply(Project target) {

                }
            }
        """
    }

    private String generateJavaProjectTypePlugin() {
        if (bindings.count { it.definition != null } > 1) {
            return generateJavaProjectTypeWithMultipleBindings()
        }

        def modifiers = maybeDeclareBindingModifiers()
        def implType = primaryDefinition.implementationClassName
            ? ".withUnsafeDefinitionImplementationType(${primaryDefinition.implementationClassName}.class)"
            : ""
        def ndocImplTypes = buildModelImplementations.collect {
            ".withNestedBuildModelImplementationType(${primaryDefinition.className}.${it.interfaceName}.class, ${it.implClassName}.class)"
        }.join("")

        return """
            package ${packageName};

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.tasks.Nested;
            import ${ProjectTypeApplyAction.class.name};
            import ${ProjectFeatureApplicationContext.class.name};
            import ${ProjectTypeBinding.class.name};
            import ${BindsProjectType.class.name};
            import ${ProjectTypeBindingBuilder.class.name};
            import javax.inject.Inject;

            @${BindsProjectType.class.simpleName}(${pluginClassName}.Binding.class)
            abstract public class ${pluginClassName} implements Plugin<Project> {

                static class Binding implements ${ProjectTypeBinding.class.simpleName} {
                    public void bind(${ProjectTypeBindingBuilder.class.simpleName} builder) {
                        builder.bindProjectType("${name}", ${primaryDefinition.className}.class, ${pluginClassName}.ApplyAction.class)
                        ${implType}${ndocImplTypes}${modifiers};
                    }
                }

                static abstract class ApplyAction implements ${ProjectTypeApplyAction.class.name}<${primaryDefinition.className}, ${primaryDefinition.fullyQualifiedBuildModelClassName}> {
                    @javax.inject.Inject
                    public ApplyAction() { }

                    ${generateJavaTypeApplyActionServices()}

                    @Override
                    public void apply(${ProjectFeatureApplicationContext.class.name} context, ${primaryDefinition.className} definition, ${primaryDefinition.fullyQualifiedBuildModelClassName} model) {
                        System.out.println("Binding " + ${primaryDefinition.className}.class.getSimpleName());

                        ${generateTypeApplyActionBody()}
                    }
                }

                ${generateBuildModelImplClasses()}

                @Override
                public void apply(Project target) {
                    System.out.println("Applying " + getClass().getSimpleName());
                }
            }
        """
    }

    private String generateJavaProjectTypeWithMultipleBindings() {
        // Each binding gets its own ApplyAction class since each
        // has different definition/model type parameters
        def allBindings = bindings.collect { [name: it.name, definition: it.definition] }

        def bindCalls = allBindings.collect { binding ->
            "builder.bindProjectType(\"${binding.name}\", ${binding.definition.className}.class, ${binding.definition.className}ApplyAction.class);"
        }.join("\n")

        def applyActionClasses = allBindings.collect { binding ->
            def bindingDefinition = binding.definition
            """
                static abstract class ${bindingDefinition.className}ApplyAction implements ${ProjectTypeApplyAction.class.name}<${bindingDefinition.className}, ${bindingDefinition.fullyQualifiedBuildModelClassName}> {
                    @javax.inject.Inject public ${bindingDefinition.className}ApplyAction() { }

                    @javax.inject.Inject
                    abstract protected ${TaskRegistrar.class.name} getTaskRegistrar();

                    @Override
                    public void apply(${ProjectFeatureApplicationContext.class.name} context, ${bindingDefinition.className} definition, ${bindingDefinition.fullyQualifiedBuildModelClassName} model) {
                        System.out.println("Binding " + ${bindingDefinition.className}.class.getSimpleName());
                        ${bindingDefinition.getBuildModelMapping(language)}
                        getTaskRegistrar().register("print${bindingDefinition.className}Configuration", DefaultTask.class, task -> {
                            task.doLast("print restricted extension content", t -> {
                                ${bindingDefinition.displayDefinitionPropertyValues(language)}
                                ${bindingDefinition.displayModelPropertyValues(language)}
                            });
                        });
                    }
                }
            """
        }.join("\n")

        return """
            package ${packageName};

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.tasks.Nested;
            import ${ProjectTypeApplyAction.class.name};
            import ${ProjectFeatureApplicationContext.class.name};
            import ${BindsProjectType.class.name};
            import javax.inject.Inject;

            @${BindsProjectType.class.simpleName}(${pluginClassName}.Binding.class)
            abstract public class ${pluginClassName} implements Plugin<Project> {
                static class Binding implements ${ProjectTypeBinding.class.name} {
                    public void bind(${ProjectTypeBindingBuilder.class.name} builder) {
                        ${bindCalls}
                    }
                }

                ${applyActionClasses}

                @Override
                public void apply(Project target) {
                    System.out.println("Applying " + getClass().getSimpleName());
                }
            }
        """
    }

    private String generateJavaProjectFeaturePlugin() {
        if (hasNoBuildModel) {
            return generateJavaFeatureWithNoBuildModel()
        }
        if (bindings.count { it.bindingTypeClassName != null } > 1) {
            return generateJavaFeatureWithMultipleBindingTargets()
        }
        return generateJavaFeaturePlugin()
    }

    private String generateJavaFeaturePlugin() {
        def modifiers = maybeDeclareBindingModifiers()
        def implType = primaryDefinition.implementationClassName
            ? ".withUnsafeDefinitionImplementationType(${primaryDefinition.implementationClassName}.class)"
            : ""
        def bmImplType = primaryDefinition.buildModelFullImplementationClassName
            ? ".withBuildModelImplementationType(${primaryDefinition.buildModelFullImplementationClassName}.class)"
            : ""

        def parentType = getParentTypeForApplyAction()
        def buildModelType = primaryDefinition.buildModelFullPublicClassName

        return """
            package ${packageName};

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import ${BindsProjectFeature.class.name};
            import ${ProjectFeatureBindingBuilder.class.name};
            import static ${ProjectFeatureBindingBuilder.class.name}.bindingToTargetDefinition;
            import ${ProjectFeatureBinding.class.name};
            import ${ProjectFeatureApplyAction.class.name};
            import ${ProjectFeatureApplicationContext.class.name};
            import ${BuildModel.class.name};

            @${BindsProjectFeature.class.simpleName}(${pluginClassName}.Binding.class)
            public class ${pluginClassName} implements Plugin<Project> {

                static class Binding implements ${ProjectFeatureBinding.class.simpleName} {
                    @Override public void bind(${ProjectFeatureBindingBuilder.class.simpleName} builder) {
                        builder.${bindingMethodName}(
                            "${name}",
                            ${primaryDefinition.className}.class,
                            ${bindingTypeClassName}.class,
                            ${pluginClassName}.ApplyAction.class
                        )
                        ${implType}${bmImplType}${modifiers};
                    }
                }

                static abstract class ApplyAction implements ${ProjectFeatureApplyAction.class.simpleName}<${primaryDefinition.className}, ${buildModelType}, ${parentType}> {
                    @javax.inject.Inject public ApplyAction() { }

                    ${generateJavaFeatureApplyActionServices()}

                    @Override
                    public void apply(${ProjectFeatureApplicationContext.class.simpleName} context, ${primaryDefinition.className} definition, ${buildModelType} model, ${parentType} parent) {
                        System.out.println("Binding ${primaryDefinition.className}");
                        System.out.println("${name} model class: " + model.getClass().getSimpleName());
                        System.out.println("${name} parent model class: " + context.getBuildModel(parent).getClass().getSimpleName());

                        ${buildModelMappingForLanguage()}

                        getTaskRegistrar().register("print${primaryDefinition.className}Configuration", task -> {
                            task.doLast(t -> {
                                ${displayDefinitionValuesForLanguage()}
                                ${displayModelValuesForLanguage()}
                            });
                        });
                    }
                }

                @Override
                public void apply(Project project) {

                }
            }
        """
    }

    private String generateJavaFeatureWithNoBuildModel() {
        def modifiers = maybeDeclareBindingModifiers()

        return """
            package ${packageName};

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import ${BindsProjectFeature.class.name};
            import ${ProjectFeatureBindingBuilder.class.name};
            import static ${ProjectFeatureBindingBuilder.class.name}.bindingToTargetDefinition;
            import ${ProjectFeatureBinding.class.name};
            import ${ProjectFeatureApplyAction.class.name};
            import ${ProjectFeatureApplicationContext.class.name};
            import org.gradle.features.binding.BuildModel;

            @${BindsProjectFeature.class.simpleName}(${pluginClassName}.Binding.class)
            public class ${pluginClassName} implements Plugin<Project> {

                static class Binding implements ${ProjectFeatureBinding.class.simpleName} {
                    @Override public void bind(${ProjectFeatureBindingBuilder.class.simpleName} builder) {
                        builder.${bindingMethodName}(
                            "${name}",
                            ${primaryDefinition.className}.class,
                            ${bindingTypeClassName}.class,
                            ${pluginClassName}.ApplyAction.class
                        )${modifiers};
                    }
                }

                static abstract class ApplyAction implements ${ProjectFeatureApplyAction.class.name}<${primaryDefinition.className}, BuildModel.None, ${bindingTypeClassName}> {
                    @javax.inject.Inject public ApplyAction() { }

                    ${generateJavaFeatureApplyActionServices()}

                    @Override
                    public void apply(${ProjectFeatureApplicationContext.class.name} context, ${primaryDefinition.className} definition, BuildModel.None model, ${bindingTypeClassName} parent) {
                        System.out.println("Binding ${primaryDefinition.className}");
                        System.out.println("${name} model class: " + model.getClass().getSimpleName());

                        ${customApplyActionCode}

                        getTaskRegistrar().register("print${primaryDefinition.className}Configuration", task -> {
                            task.doLast(t -> {
                                ${displayDefinitionValuesForLanguage()}
                            });
                        });
                    }
                }

                @Override
                public void apply(Project project) {
                }
            }
        """
    }

    private String generateJavaFeatureWithMultipleBindingTargets() {
        def modifiers = maybeDeclareBindingModifiers()
        def buildModelType = primaryDefinition.buildModelFullPublicClassName

        // Each binding target gets its own concrete ApplyAction subclass
        def allTargets = bindings.collect { it.bindingTypeClassName }.findAll { it != null }
        def bindCalls = allTargets.withIndex().collect { target, idx ->
            def simpleTarget = target.tokenize('.').last()
            """builder.${bindingMethodName}(
                            "${name}",
                            ${primaryDefinition.className}.class,
                            ${target}.class,
                            ${pluginClassName}.${simpleTarget}ApplyAction.class
                        )${modifiers};"""
        }.join("\n                        ")

        def concreteActions = allTargets.withIndex().collect { target, idx ->
            def simpleTarget = target.tokenize('.').last()
            def parentType = (bindingMethodName == "bindProjectFeatureToBuildModel")
                ? "${Definition.class.name}<${target}>"
                : target
            """
                static abstract class ${simpleTarget}ApplyAction extends BaseApplyAction<${parentType}> {
                    @javax.inject.Inject public ${simpleTarget}ApplyAction() { }
                    @Override protected String getTaskName() { return "print${primaryDefinition.className}${idx + 1}Configuration"; }
                }
            """
        }.join("\n")

        return """
            package ${packageName};

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import ${BindsProjectFeature.class.name};
            import ${ProjectFeatureBindingBuilder.class.name};
            import static ${ProjectFeatureBindingBuilder.class.name}.bindingToTargetDefinition;
            import ${ProjectFeatureBinding.class.name};
            import ${ProjectFeatureApplyAction.class.name};
            import ${ProjectFeatureApplicationContext.class.name};
            import ${Definition.class.name};

            @${BindsProjectFeature.class.simpleName}(${pluginClassName}.Binding.class)
            public class ${pluginClassName} implements Plugin<Project> {

                static class Binding implements ${ProjectFeatureBinding.class.simpleName} {
                    @Override public void bind(${ProjectFeatureBindingBuilder.class.simpleName} builder) {
                        ${bindCalls}
                    }
                }

                static abstract class BaseApplyAction<P extends ${Definition.class.name}<?>> implements ${ProjectFeatureApplyAction.class.name}<${primaryDefinition.className}, ${buildModelType}, P> {
                    @javax.inject.Inject public BaseApplyAction() { }

                    ${generateJavaFeatureApplyActionServices()}

                    abstract protected String getTaskName();

                    @Override
                    public void apply(${ProjectFeatureApplicationContext.class.name} context, ${primaryDefinition.className} definition, ${buildModelType} model, P parent) {
                        System.out.println("Binding ${primaryDefinition.className}");
                        System.out.println("${name} model class: " + model.getClass().getSimpleName());

                        ${buildModelMappingForLanguage()}

                        getTaskRegistrar().register(getTaskName(), task -> {
                            task.doLast(t -> {
                                ${displayDefinitionValuesForLanguage()}
                                ${displayModelValuesForLanguage()}
                            });
                        });
                    }
                }

                ${concreteActions}

                @Override
                public void apply(Project project) {
                }
            }
        """
    }

    // --- Kotlin code generation ---

    private String getKotlinClassContent() {
        if (!exposesBindings) {
            return generateKotlinNoBindingsPlugin()
        }
        if (kind == PluginKind.PROJECT_TYPE) {
            if (bindingStyle == BindingStyle.REIFIED) {
                return generateKotlinReifiedProjectTypePlugin()
            }
            return generateKotlinProjectTypePlugin()
        }
        if (hasNoBuildModel) {
            return generateKotlinReifiedFeaturePlugin()
        }
        return generateKotlinFeaturePlugin()
    }

    private String generateKotlinNoBindingsPlugin() {
        return """
            package ${packageName}

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            abstract class ${pluginClassName} : Plugin<Project> {
                override fun apply(target: Project) {
                }
            }
        """
    }

    private String generateKotlinProjectTypePlugin() {
        def modifiers = maybeDeclareBindingModifiers()
        def implType = primaryDefinition.implementationClassName
            ? ".withUnsafeDefinitionImplementationType(${primaryDefinition.implementationClassName}::class.java)"
            : ""

        return """
            package ${packageName}

            import org.gradle.api.Task
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.provider.ListProperty
            import org.gradle.api.provider.Property
            import org.gradle.api.tasks.Nested
            import ${ProjectTypeBinding.class.name}
            import ${BindsProjectType.class.name}
            import ${ProjectTypeBindingBuilder.class.name}
            import ${ProjectTypeApplyAction.class.name}
            import javax.inject.Inject

            @${BindsProjectType.class.simpleName}(${pluginClassName}.Binding::class)
            class ${pluginClassName} : Plugin<Project> {

                class Binding : ${ProjectTypeBinding.class.simpleName} {
                    override fun bind(builder: ${ProjectTypeBindingBuilder.class.simpleName}) {
                        builder.bindProjectType("${name}", ${primaryDefinition.className}::class.java, ${pluginClassName}.ApplyAction::class.java)
                        ${implType}${modifiers}
                    }
                }

                abstract class ApplyAction @Inject constructor() : ${ProjectTypeApplyAction.class.simpleName}<${primaryDefinition.className}, ${primaryDefinition.fullyQualifiedBuildModelClassName}> {

                    @get:javax.inject.Inject
                    abstract val taskRegistrar: ${TaskRegistrar.class.name}

                    override fun apply(context: ${ProjectFeatureApplicationContext.class.name}, definition: ${primaryDefinition.className}, model: ${primaryDefinition.fullyQualifiedBuildModelClassName}) {
                        println("Binding " + ${primaryDefinition.className}::class.simpleName)

                        ${buildModelMappingForLanguage()}

                        taskRegistrar.register("print${primaryDefinition.className}Configuration") { task: Task ->
                            task.doLast { _: Task ->
                                ${displayDefinitionValuesForLanguage()}
                                ${displayModelValuesForLanguage()}
                            }
                        }
                    }
                }

                override fun apply(project: Project) {
                    println("Applying " + this::class.java.simpleName)
                }
            }
        """
    }

    private String generateKotlinReifiedProjectTypePlugin() {
        def modifiers = maybeDeclareBindingModifiers()
        def implType = primaryDefinition.implementationClassName
            ? ".withUnsafeDefinitionImplementationType(${primaryDefinition.implementationClassName}::class.java)"
            : ""

        return """
            package ${packageName}

            import org.gradle.api.Task
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.provider.ListProperty
            import org.gradle.api.provider.Property
            import org.gradle.api.tasks.Nested
            import ${ProjectTypeBinding.class.name}
            import ${BindsProjectType.class.name}
            import ${ProjectTypeBindingBuilder.class.name}
            import ${ProjectTypeApplyAction.class.name}
            import org.gradle.features.dsl.bindProjectType
            import javax.inject.Inject

            @${BindsProjectType.class.simpleName}(${pluginClassName}.Binding::class)
            class ${pluginClassName} : Plugin<Project> {

                class Binding : ${ProjectTypeBinding.class.simpleName} {
                    override fun bind(builder: ${ProjectTypeBindingBuilder.class.simpleName}) {
                        builder.bindProjectType("${name}", ${pluginClassName}.ApplyAction::class)
                        ${implType}${modifiers}
                    }
                }

                abstract class ApplyAction @Inject constructor() : ${ProjectTypeApplyAction.class.simpleName}<${primaryDefinition.className}, ${primaryDefinition.fullyQualifiedBuildModelClassName}> {

                    @get:javax.inject.Inject
                    abstract val taskRegistrar: ${TaskRegistrar.class.name}

                    override fun apply(context: ${ProjectFeatureApplicationContext.class.name}, definition: ${primaryDefinition.className}, model: ${primaryDefinition.fullyQualifiedBuildModelClassName}) {
                        println("Binding " + ${primaryDefinition.className}::class.simpleName)

                        ${buildModelMappingForLanguage()}

                        taskRegistrar.register("print${primaryDefinition.className}Configuration") { task: Task ->
                            task.doLast { _: Task ->
                                ${displayDefinitionValuesForLanguage()}
                                ${displayModelValuesForLanguage()}
                            }
                        }
                    }
                }

                override fun apply(project: Project) {
                    println("Applying " + this::class.java.simpleName)
                }
            }
        """
    }

    private String generateKotlinFeaturePlugin() {
        def modifiers = maybeDeclareBindingModifiers()
        def parentType = getParentTypeForApplyAction()
        def buildModelType = primaryDefinition.buildModelFullPublicClassName

        return """
            package ${packageName}

            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import ${BindsProjectFeature.class.name}
            import ${ProjectFeatureBindingBuilder.class.name}
            import ${ProjectFeatureBinding.class.name}
            import ${ProjectFeatureApplyAction.class.name}
            import ${ProjectFeatureApplicationContext.class.name}
            import javax.inject.Inject

            @${BindsProjectFeature.class.simpleName}(${pluginClassName}.Binding::class)
            class ${pluginClassName} : Plugin<Project> {

                class Binding : ${ProjectFeatureBinding.class.simpleName} {
                    override fun bind(builder: ${ProjectFeatureBindingBuilder.class.simpleName}) {
                        builder.${bindingMethodName}("${name}", ${primaryDefinition.className}::class.java, ${bindingTypeClassName}::class.java, ${pluginClassName}.ApplyAction::class.java)${modifiers}
                    }
                }

                abstract class ApplyAction @Inject constructor() : ${ProjectFeatureApplyAction.class.simpleName}<${primaryDefinition.className}, ${buildModelType}, ${parentType}> {

                    @get:Inject
                    abstract val taskRegistrar: ${TaskRegistrar.class.name}

                    @get:Inject
                    abstract val projectFeatureLayout: ${ProjectFeatureLayout.class.name}

                    @get:Inject
                    abstract val providerFactory: ${ProviderFactory.class.name}

                    override fun apply(context: ${ProjectFeatureApplicationContext.class.name}, definition: ${primaryDefinition.className}, model: ${buildModelType}, parent: ${parentType}) {
                        println("Binding ${primaryDefinition.className}")
                        println("${name} model class: " + model::class.simpleName)
                        println("${name} parent model class: " + context.getBuildModel(parent)::class.simpleName)

                        ${buildModelMappingForLanguage()}

                        taskRegistrar.register("print${primaryDefinition.className}Configuration") { task ->
                            task.doLast { _ ->
                                ${displayDefinitionValuesForLanguage()}
                                ${displayModelValuesForLanguage()}
                            }
                        }
                    }
                }

                override fun apply(project: Project) {
                }
            }
        """
    }

    private String generateKotlinReifiedFeaturePlugin() {
        def modifiers = maybeDeclareBindingModifiers()

        return """
            package ${packageName}

            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import ${BindsProjectFeature.class.name}
            import ${ProjectFeatureBindingBuilder.class.name}
            import ${ProjectFeatureBinding.class.name}
            import ${ProjectFeatureApplyAction.class.name}
            import ${ProjectFeatureApplicationContext.class.name}
            import ${BuildModel.class.name}
            import org.gradle.features.dsl.bindProjectFeature
            import javax.inject.Inject

            @${BindsProjectFeature.class.simpleName}(${pluginClassName}.Binding::class)
            class ${pluginClassName} : Plugin<Project> {

                class Binding : ${ProjectFeatureBinding.class.simpleName} {
                    override fun bind(builder: ${ProjectFeatureBindingBuilder.class.simpleName}) {
                        builder.bindProjectFeature("${name}", ${pluginClassName}.ApplyAction::class)${modifiers}
                    }
                }

                abstract class ApplyAction @Inject constructor() : ${ProjectFeatureApplyAction.class.simpleName}<${primaryDefinition.className}, BuildModel.None, ${bindingTypeClassName}> {

                    @get:Inject
                    abstract val taskRegistrar: ${TaskRegistrar.class.name}

                    override fun apply(context: ${ProjectFeatureApplicationContext.class.name}, definition: ${primaryDefinition.className}, model: BuildModel.None, parent: ${bindingTypeClassName}) {
                        println("Binding ${primaryDefinition.className}")
                        println("${name} model class: " + model::class.simpleName)

                        taskRegistrar.register("print${primaryDefinition.className}Configuration") { task ->
                            task.doLast { _ ->
                                ${displayDefinitionValuesForLanguage()}
                            }
                        }
                    }
                }

                override fun apply(project: Project) {
                }
            }
        """
    }

    // --- Service injection helpers ---

    private String generateJavaTypeApplyActionServices() {
        if (!applyActionDeclaration.injectedServices.isEmpty()) {
            return generateCustomServices(applyActionDeclaration.injectedServices, false)
        }
        return """
                    @javax.inject.Inject
                    abstract protected ${TaskRegistrar.class.name} getTaskRegistrar();
        """
    }

    private String generateJavaFeatureApplyActionServices() {
        if (!applyActionDeclaration.injectedServices.isEmpty()) {
            return generateCustomServices(applyActionDeclaration.injectedServices, true)
        }
        return """
                    @javax.inject.Inject
                    abstract protected ${TaskRegistrar.class.name} getTaskRegistrar();

                    @javax.inject.Inject
                    abstract protected ${ProjectFeatureLayout.class.name} getProjectFeatureLayout();

                    @javax.inject.Inject
                    abstract protected ${ProviderFactory.class.name} getProviderFactory();
        """
    }

    private String generateCustomServices(List<ServiceDeclaration> services, boolean isFeature) {
        def lines = []
        services.each { service ->
            if (service.name == "project") {
                lines << """
                    @javax.inject.Inject
                    abstract protected Project getProject(); // Unsafe Service

                    protected org.gradle.api.tasks.TaskContainer getTaskRegistrar() {
                        return getProject().getTasks();
                    }"""
                if (isFeature) {
                    lines << """
                    @javax.inject.Inject
                    abstract protected ${ProjectFeatureLayout.class.name} getProjectFeatureLayout();

                    @javax.inject.Inject
                    abstract protected ${ProviderFactory.class.name} getProviderFactory();"""
                }
            } else if (service.name == "unknown") {
                lines << """
                    interface UnknownService extends ${TaskRegistrar.class.name} { }

                    protected ${TaskRegistrar.class.name} getTaskRegistrar() {
                        return getUnknownService();
                    }

                    @javax.inject.Inject
                    abstract protected UnknownService getUnknownService();"""
                if (isFeature) {
                    lines << """
                    @javax.inject.Inject
                    abstract protected ${ProjectFeatureLayout.class.name} getProjectFeatureLayout();

                    @javax.inject.Inject
                    abstract protected ${ProviderFactory.class.name} getProviderFactory();"""
                }
            } else {
                lines << """
                    @javax.inject.Inject
                    abstract protected ${service.type.name} get${JavaSources.capitalize(service.name)}();"""
            }
        }
        return lines.join("\n")
    }

    // --- Apply action body generation ---

    private String generateTypeApplyActionBody() {
        if (applyActionDeclaration.readsValuesEagerly) {
            return generateEagerReadApplyBody()
        }
        return """
                        ${buildModelMappingForLanguage()}

                        getTaskRegistrar().register("print${primaryDefinition.className}Configuration", DefaultTask.class, task -> {
                            task.doLast("print restricted extension content", t -> {
                                ${displayDefinitionValuesForLanguage()}
                                ${displayModelValuesForLanguage()}
                            });
                        });

                        ${customApplyActionCode}
        """
    }

    private String generateEagerReadApplyBody() {
        def eagerReads = []
        def printStatements = []
        primaryDefinition.properties.each { property ->
            def varName = "${property.name}AtApplyTime"
            eagerReads << "String ${varName} = definition.get${JavaSources.capitalize(property.name)}().get();"
            printStatements << """System.out.println("apply time ${property.name} = " + ${varName});"""
        }
        primaryDefinition.nestedTypes.findAll { !it.isNdoc }.each { nestedType ->
            nestedType.properties.each { property ->
                def varName = "${nestedType.name}${JavaSources.capitalize(property.name)}AtApplyTime"
                eagerReads << "String ${varName} = definition.get${JavaSources.capitalize(nestedType.name)}().get${JavaSources.capitalize(property.name)}().get();"
                printStatements << """System.out.println("apply time ${nestedType.name}.${property.name} = " + ${varName});"""
            }
        }

        return """
                        // Eagerly read values at apply time.
                        // These reads throw MissingValueException if the definition
                        // hasn't been configured yet - that is what this fixture tests against.
                        ${eagerReads.join("\n")}

                        ${buildModelMappingForLanguage()}

                        getTaskRegistrar().register("printApplyTimeValues", DefaultTask.class, task -> {
                            task.doLast("print", t -> {
                                ${printStatements.join("\n")}
                            });
                        });
        """
    }

    private String generateBuildModelImplClasses() {
        return buildModelImplementations.collect { implementation ->
            def propertyDeclarations = implementation.properties.collect { property ->
                "public abstract ${getPropertyReturnType(property)} get${JavaSources.capitalize(property.name)}();"
            }.join("\n")
            """
                public static abstract class ${implementation.implClassName} implements ${primaryDefinition.className}.${implementation.interfaceName} {
                    ${propertyDeclarations}
                }
            """
        }.join("\n")
    }

    // --- Helpers ---

    private String getParentTypeForApplyAction() {
        if (bindingMethodName == "bindProjectFeatureToBuildModel") {
            return "${Definition.class.name}<${bindingTypeClassName}>"
        }
        return bindingTypeClassName
    }

    private String maybeDeclareBindingModifiers() {
        return bindingModifiers.isEmpty() ? "" : bindingModifiers.collect { ".${it}" }.join("")
    }

    /** Returns the build model mapping code for the current language. */
    private String buildModelMappingForLanguage() {
        return primaryDefinition.getBuildModelMapping(language)
    }

    /** Returns the definition property display code for the current language. */
    private String displayDefinitionValuesForLanguage() {
        return primaryDefinition.displayDefinitionPropertyValues(language)
    }

    /** Returns the model property display code for the current language. */
    private String displayModelValuesForLanguage() {
        return primaryDefinition.displayModelPropertyValues(language)
    }

    private static String getPropertyReturnType(PropertyDeclaration property) {
        if (property.isList) {
            return "org.gradle.api.provider.ListProperty<${property.type.simpleName}>"
        }
        return "org.gradle.api.provider.Property<${property.type.simpleName}>"
    }
}
