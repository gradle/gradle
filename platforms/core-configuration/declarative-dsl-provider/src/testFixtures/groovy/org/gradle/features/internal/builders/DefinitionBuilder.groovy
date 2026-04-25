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

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.Directory
import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition
import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.Definition
import org.gradle.features.internal.builders.dsl.ClosureConfigure
import org.gradle.features.internal.builders.dsl.HasInjectedServices
import org.gradle.features.internal.builders.dsl.HasNestedTypes
import org.gradle.features.internal.builders.dsl.HasProperties
import org.gradle.features.internal.builders.dsl.HasSharedRefInNestedTypes
import org.gradle.features.internal.builders.dsl.HasUndiscoverableNested
import org.gradle.test.fixtures.plugin.PluginBuilder

/**
 * Generates Java source code for a definition interface or abstract class.
 *
 * <p>This is a compositional builder that replaces the 21 separate definition builder subclasses.
 * Instead of choosing a specific subclass for each variation, you declare what the generated
 * definition should contain: properties, nested types, services, NDOC containers, build model,
 * and shape (interface vs abstract class).</p>
 *
 * <p>The builder produces one or more Java source files when {@link #build} is called:</p>
 * <ul>
 *     <li>The public type (interface or abstract class)</li>
 *     <li>Optionally, a separate implementation type class (when {@link #implementationType} is called)</li>
 *     <li>Optionally, a parent type interface (when {@link #parentDefinition} is configured)</li>
 * </ul>
 *
 * <p>In addition to generating source files, this builder provides derived accessor methods
 * ({@link #getBuildModelMapping()}, {@link #displayDefinitionPropertyValues()}, etc.) that are
 * consumed by {@link PluginClassBuilder} to generate the mapping and display code in plugin apply actions.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * definition("TestProjectTypeDefinition") {
 *     buildModel("ModelType") { property "id", String }
 *     property "id", String
 *     nested("foo", "Foo") {
 *         implementsDefinition("FooBuildModel") { property "barProcessed", String }
 *         property "bar", String
 *     }
 * }
 * </pre>
 */
class DefinitionBuilder implements HasProperties, HasNestedTypes, HasInjectedServices, HasSharedRefInNestedTypes, HasUndiscoverableNested {
    /** The simple class name of the definition (e.g. "TestProjectTypeDefinition"). */
    String className

    /** The Java package for generated source files. */
    String packageName = "org.gradle.test"

    /** Whether to generate an interface or abstract class. */
    TypeShape shape = TypeShape.INTERFACE

    /** The class name of the separate implementation type, or null if there is none. */
    String implementationClassName

    /** Dependencies declaration, or null if this definition does not support dependencies. */
    DependenciesDeclaration dependenciesDeclaration = null

    /** A parent definition whose injected services are inherited via an extends clause. */
    DefinitionBuilder parentDefinition = null

    /** The build model inner interface declaration. Null means no build model. */
    BuildModelDeclaration buildModel = null

    /** When true, the definition extends {@code Definition<BuildModel.None>} with no inner build model interface. */
    boolean usesNoBuildModel = false

    /**
     * When true, generates {@code maybe${X}Configured()} helpers, the backing
     * {@code is${X}Configured} fields, the side-effect assignment in each nested
     * getter, and the corresponding print call in {@link #displayDefinitionPropertyValues}.
     * This is test-only scaffolding used by tests that assert on
     * {@code "(${name} is configured)"} output.
     *
     * <p>Silently no-op on {@link TypeShape#INTERFACE} definitions: the underlying emit
     * sites already require {@code ABSTRACT_CLASS}, and this flag is strictly
     * additive. Also silently no-op for NDOC and undiscoverable nested types,
     * which never carry this scaffolding.</p>
     */
    boolean showsConfigureInvocations = false

    /** Top-level properties on this definition. */
    List<PropertyDeclaration> properties = []

    /** Services injected into this definition via {@code @Inject}. */
    List<ServiceDeclaration> injectedServices = []

    /** Nested types (both {@code @Nested} and NDOC) within this definition. */
    List<PropertyTypeDeclaration> nestedTypes = []

    DefinitionBuilder() {}
    DefinitionBuilder(String className) { this.className = className }

    // --- Derived accessors used by PluginClassBuilder ---

    /** Returns the fully qualified class name (e.g. "org.gradle.test.TestProjectTypeDefinition"). */
    String getFullyQualifiedPublicTypeClassName() {
        return packageName + "." + className
    }

    /**
     * Returns the fully qualified build model class name, or null if no build model.
     * When a parent definition exists, the build model is declared on the parent, so the
     * parent's class name is used (required for Kotlin which can't resolve inherited inner types).
     */
    String getFullyQualifiedBuildModelClassName() {
        if (!buildModel) {
            return null
        }
        def owningClassName = parentDefinition ? packageName + ".Parent" + className : fullyQualifiedPublicTypeClassName
        return owningClassName + "." + buildModel.className
    }

    /**
     * Returns the build model type as used in apply action type parameters.
     * Falls back to {@code BuildModel.None} when no build model is declared.
     */
    String getBuildModelFullPublicClassName() {
        return buildModel ? className + "." + buildModel.className : "${BuildModel.class.name}.None"
    }

    /** Returns the build model implementation class name, or null if there is no separate implementation. */
    String getBuildModelFullImplementationClassName() {
        return (buildModel?.implementationClassName) ? className + "." + buildModel.implementationClassName : null
    }

    // --- DSL methods ---

    /**
     * Declares a {@code BuildModel} inner interface on this definition with the given class name,
     * replacing any existing build model and resetting {@link #usesNoBuildModel} to {@code false}.
     *
     * @param modelClassName the simple class name for the build model (e.g. "ModelType")
     * @param config         optional closure delegating to {@link BuildModelDeclaration}
     */
    void buildModel(String modelClassName,
        @DelegatesTo(value = BuildModelDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config = {}
    ) {
        this.buildModel = new BuildModelDeclaration(className: modelClassName)
        this.usesNoBuildModel = false
        ClosureConfigure.configure(this.buildModel, config)
    }

    /**
     * Configures the existing {@code BuildModel} inner interface (e.g. to add properties).
     * The build model must already exist (set by the factory method or a prior call to
     * {@link #buildModel(String, Closure)}).
     *
     * @param config closure delegating to {@link BuildModelDeclaration}
     */
    void buildModel(
        @DelegatesTo(value = BuildModelDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config
    ) {
        if (this.buildModel == null) {
            throw new IllegalStateException(
                "buildModel(Closure) called with no existing build model to configure. " +
                "Pass a class name to create one, e.g. buildModel(\"ModelType\") { ... }."
            )
        }
        ClosureConfigure.configure(this.buildModel, config)
    }

    /**
     * Removes the build model inner interface and makes the definition extend {@code Definition<BuildModel.None>}.
     * This is used for feature definitions that don't produce a build model.
     */
    void noBuildModel() {
        this.buildModel = null
        this.usesNoBuildModel = true
    }

    /**
     * Enables generation of {@code maybe${X}Configured()} scaffolding for each non-NDOC,
     * non-undiscoverable nested type. Off by default. Silently no-op on {@link TypeShape#INTERFACE}.
     * See {@link #showsConfigureInvocations}.
     */
    void showConfigureInvocations() {
        this.showsConfigureInvocations = true
    }

    /** Adds a read-only property that returns the concrete type directly (e.g. {@code Directory getDir()}). */
    void readOnlyProperty(String name, Class type) {
        properties.add(PropertyDeclaration.readOnly(name, type))
    }

    /** Adds a Java Bean property with getter/setter pair instead of {@code Property<T>}. */
    void javaBeanProperty(String name, Class type) {
        properties.add(PropertyDeclaration.javaBean(name, type))
    }

    /** Adds a Java Bean property with getter/setter pair, with optional configuration (e.g. shape). */
    void javaBeanProperty(String name, Class type,
        @DelegatesTo(value = PropertyDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config
    ) {
        properties.add(ClosureConfigure.configure(PropertyDeclaration.javaBean(name, type), config))
    }

    /** Sets the shape (interface or abstract class) of the generated definition. */
    void shape(TypeShape s) { this.shape = s }

    /**
     * Declares that this definition has a separate implementation type class.
     * This generates an additional file with an abstract class that implements the definition interface.
     */
    void implementationType(String implClassName) {
        this.implementationClassName = implClassName
    }

    /**
     * Declares a dependencies block with named dependency collectors.
     *
     * @param config closure delegating to {@link DependenciesDeclaration}
     */
    void dependencies(
        @DelegatesTo(value = DependenciesDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config
    ) {
        this.dependenciesDeclaration = ClosureConfigure.configure(new DependenciesDeclaration(), config)
    }

    /**
     * Declares a parent definition whose injected services are inherited.
     * Generates a parent interface and makes this definition extend it.
     *
     * @param config closure to configure the parent (typically just adding injected services)
     */
    void parentDefinition(
        @DelegatesTo(value = DefinitionBuilder, strategy = Closure.DELEGATE_FIRST)
        Closure config
    ) {
        this.parentDefinition = ClosureConfigure.configure(new DefinitionBuilder(), config)
    }

    // --- Code generation ---

    /**
     * Generates Java source files and writes them to the plugin builder.
     * Produces 1-3 files depending on configuration: the public type, an optional
     * implementation type, and an optional parent type.
     */
    void build(PluginBuilder pluginBuilder) {
        validateShapes()
        pluginBuilder.file("src/main/java/${packageName.replace('.', '/')}/${className}.java").text = getPublicTypeClassContent()
        if (implementationClassName) {
            pluginBuilder.file("src/main/java/${packageName.replace('.', '/')}/${implementationClassName}.java").text = getImplementationTypeClassContent()
        }
        if (parentDefinition) {
            def parentClassName = "Parent${className}"
            pluginBuilder.file("src/main/java/${packageName.replace('.', '/')}/${parentClassName}.java").text = getParentClassContent(parentClassName)
        }
        if (dependenciesDeclaration) {
            pluginBuilder.file("src/main/java/${packageName.replace('.', '/')}/${dependenciesDeclaration.interfaceName}.java").text = getDependenciesInterfaceContent()
        }
    }

    String getPublicTypeClassContent() {
        if (shape == TypeShape.ABSTRACT_CLASS) {
            return generateAbstractClassContent(className)
        }
        if (parentDefinition) {
            return generateChildInterfaceContent(className)
        }
        return generateInterfaceContent(className)
    }

    /**
     * Returns true if the outer abstract class needs a backing field (and corresponding
     * constructor initialization) for this nested type. NDOC never uses field-backed
     * storage. Undiscoverable and shared-ref always do (their access patterns require it).
     * Regular nested types use field-backed storage only when their effective shape is
     * {@link TypeShape#ABSTRACT_CLASS}.
     */
    private boolean needsBackingField(PropertyTypeDeclaration nested) {
        switch (nested.kind) {
            case NestedKind.NDOC:
                return false
            case NestedKind.UNDISCOVERABLE:
            case NestedKind.SHARED_REF:
                return true
            case NestedKind.PLAIN:
                return NestedRenderer.effectiveShapeOf(nested, this.shape) == TypeShape.ABSTRACT_CLASS
            default:
                throw new IllegalStateException("Unreachable: unknown NestedKind ${nested.kind}")
        }
    }

    /**
     * Validates cross-cutting constraints on nested-type shapes. Called from {@link #build}
     * before any emission. Currently enforces:
     * <ul>
     *     <li>{@link #showsConfigureInvocations} requires every regular top-level nested type
     *         to be effectively {@link TypeShape#ABSTRACT_CLASS}, because the side-effect
     *         scaffolding is emitted into the concrete getter path only.</li>
     * </ul>
     */
    private void validateShapes() {
        if (shape == TypeShape.ABSTRACT_CLASS && showsConfigureInvocations) {
            def offenders = nestedTypes.findAll { nested ->
                nested.kind == NestedKind.PLAIN &&
                    NestedRenderer.effectiveShapeOf(nested, this.shape) == TypeShape.INTERFACE
            }
            if (!offenders.isEmpty()) {
                def names = offenders.collect { it.name }.join(", ")
                throw new IllegalStateException(
                    "showConfigureInvocations() requires ABSTRACT_CLASS-shaped nested types; " +
                    "the following nested properties are effectively INTERFACE: [${names}]. " +
                    "Either remove showConfigureInvocations() or drop the shape(INTERFACE) override."
                )
            }
        }
    }

    String getImplementationTypeClassContent() {
        return """
            package ${packageName};

            import org.gradle.api.Action;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;

            import javax.inject.Inject;

            public abstract class ${implementationClassName} implements ${className} {
                ${generateImplConstructorAndFields()}

                public abstract Property<String> getNonPublic();
            }
        """
    }

    // --- Build model mapping (consumed by PluginClassBuilder) ---

    /**
     * Returns code that maps definition property values to build model properties,
     * formatted for the specified language. Returns the custom mapping if set,
     * otherwise auto-derives mappings from matching property names.
     *
     * <p>In addition to the top-level mapping, includes mappings for any direct child
     * nested type that declares {@code implementsDefinition(...)} and has its own build
     * model. Nested mappings are auto-derived from properties that match by name exactly
     * (non-list only), or taken verbatim from the nested build model's custom mapping.
     * Plain {@code @Nested} nested types are accessed directly via their getter; NDOC
     * nested types are wrapped in {@code configureEach}. Undiscoverable nested types are
     * excluded from <em>automatic</em> mapping because they expose no public getter, but
     * a custom mapping supplied via {@code mapping(...)} is still emitted verbatim.</p>
     */
    String getBuildModelMapping(Language language) {
        def sections = []
        if (buildModel != null) {
            def topLevel = generateTopLevelBuildModelMapping(language)
            if (topLevel) {
                sections << topLevel
            }
        }
        nestedTypes.findAll { it.implementsDefinition && it.buildModel != null }.each { nestedType ->
            def nested = generateNestedDefinitionMapping(nestedType, language)
            if (nested) {
                sections << nested
            }
        }
        return sections.join("\n")
    }

    private String generateTopLevelBuildModelMapping(Language language) {
        if (buildModel.customMappings.containsKey(language)) {
            return buildModel.customMappings[language]
        }
        // Only simple (non-list) properties are expected to map to build model properties.
        // List properties and nested types are display-only and don't participate in mapping.
        def mappableProperties = properties.findAll { it.kind != PropertyKind.LIST_PROPERTY }
        def unmappedDefinitionProperties = mappableProperties.findAll { defProp ->
            !buildModel.properties.any { it.name == defProp.name }
        }
        if (!unmappedDefinitionProperties.isEmpty()) {
            def names = unmappedDefinitionProperties.collect { it.name }.join(", ")
            throw new IllegalStateException(
                "Definition '${className}' has properties [${names}] with no matching build model properties. " +
                "Either add matching properties to the build model or provide a custom buildModelMapping."
            )
        }
        def mappings = []
        buildModel.properties.each { buildModelProperty ->
            if (buildModelProperty.kind == PropertyKind.SHARED_REF) {
                // Shared-ref on the build model: auto-map only if the definition has a matching
                // shared-ref entry by the same accessor name and the shared type is not itself
                // definition-shaped (which would require context.getBuildModel, punted).
                def matchingRef = nestedTypes.find { it.kind == NestedKind.SHARED_REF && it.name == buildModelProperty.name }
                if (matchingRef && !matchingRef.implementsDefinition) {
                    mappings << generateSharedRefMapping(buildModelProperty.name, matchingRef, language)
                }
                return
            }
            def definitionProperty = properties.find { it.name == buildModelProperty.name }
            if (definitionProperty) {
                mappings << generatePropertyMapping(buildModelProperty, definitionProperty, language)
            }
        }
        return mappings.join("\n")
    }

    /**
     * Emits scalar-by-scalar mapping across a shared-typed property that exists on both the
     * definition and the build model by the same accessor name. Only plain scalars
     * (non-list, non-readOnly, non-javaBean) participate — these have {@code Property<T>}-shaped
     * accessors on both sides, so {@code .set(...)} works symmetrically.
     */
    private String generateSharedRefMapping(String accessorName, PropertyTypeDeclaration ref, Language language) {
        def statementEnd = language.statementEnd()
        def defExpr = language.propertyAccessor("definition", accessorName)
        def modelExpr = language.propertyAccessor("model", accessorName)
        return ref.properties.findAll { it.kind == PropertyKind.PROPERTY }.collect { scalar ->
            def defAccess = language.propertyAccessor(defExpr, scalar.name)
            def modelAccess = language.propertyAccessor(modelExpr, scalar.name)
            "${modelAccess}.set(${defAccess})${statementEnd}"
        }.join("\n")
    }

    private String generateNestedDefinitionMapping(PropertyTypeDeclaration nestedType, Language language) {
        if (nestedType.buildModel.customMappings.containsKey(language)) {
            return nestedType.buildModel.customMappings[language]
        }
        switch (nestedType.kind) {
            case NestedKind.UNDISCOVERABLE:
                // Auto-derived mappings would need definition.getFoo(), but undiscoverable
                // nested types have no public getter. Custom mappings are handled above.
                return ""
            case NestedKind.SHARED_REF:
                // Shared types are not registered via feature binding, so context.getBuildModel(...)
                // would not resolve at runtime. Shared-ref ↔ shared-ref auto-mapping happens in
                // generateTopLevelBuildModelMapping via generateSharedRefMapping; otherwise rely on
                // a custom mapping(...) block on the referring build model.
                return ""
            case NestedKind.PLAIN:
            case NestedKind.NDOC:
                break
            default:
                throw new IllegalStateException("Unreachable: unknown NestedKind ${nestedType.kind}")
        }
        def elementExpression
        if (nestedType.kind == NestedKind.NDOC) {
            elementExpression = JavaSources.decapitalize(nestedType.typeName)
        } else {
            elementExpression = language.propertyAccessor("definition", nestedType.name)
        }
        def modelExpression = "context.getBuildModel(${elementExpression})"
        def statementEnd = language.statementEnd()
        def mappings = []
        nestedType.buildModel.properties.findAll { it.kind != PropertyKind.LIST_PROPERTY }.each { bmProp ->
            def defProp = nestedType.properties.find { it.name == bmProp.name && it.kind != PropertyKind.LIST_PROPERTY }
            if (defProp) {
                def modelAccess = language.propertyAccessor(modelExpression, bmProp.name)
                def defAccess = language.propertyAccessor(elementExpression, defProp.name)
                mappings << "${modelAccess}.set(${defAccess})${statementEnd}"
            }
        }
        if (mappings.isEmpty()) {
            return ""
        }
        if (nestedType.kind != NestedKind.NDOC) {
            return mappings.join("\n")
        }
        def containerAccessor = language.propertyAccessor("definition", nestedType.name)
        def body = mappings.join("\n    ")
        if (language == Language.KOTLIN) {
            return "${containerAccessor}.configureEach { ${elementExpression} ->\n    ${body}\n}"
        }
        return "${containerAccessor}.configureEach(${elementExpression} -> {\n    ${body}\n});"
    }

    /**
     * Returns code that prints all definition property values, formatted for the specified language.
     * Used inside the plugin's task body for test verification.
     */
    String displayDefinitionPropertyValues(Language language) {
        def lines = []
        properties.each { property ->
            if (property.kind == PropertyKind.LIST_PROPERTY && dependenciesDeclaration) {
                def accessor = "definition.printList(${language.propertyAccessor('definition', property.name)}.get())"
                lines << language.printStatement("definition", property.name, accessor)
            } else {
                lines << language.printStatement("definition", property.name, generatePropertyAccess("definition", property, language))
            }
        }
        nestedTypes.each { nestedType ->
            if (nestedType.kind == NestedKind.UNDISCOVERABLE) {
                // Undiscoverable types have no public getter, so their properties cannot be
                // accessed from the task body for display.
                return
            }
            if (nestedType.kind == NestedKind.NDOC) {
                def accessor = "${language.propertyAccessor('definition', nestedType.name)}.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(\", \"))"
                lines << language.printStatement("definition", nestedType.name, accessor)
            } else {
                nestedType.properties.each { property ->
                    def parentAccessor = language.propertyAccessor("definition", nestedType.name)
                    if (property.kind == PropertyKind.LIST_PROPERTY && dependenciesDeclaration) {
                        def accessor = "definition.printList(${parentAccessor}.get${JavaSources.capitalize(property.name)}().get())"
                        lines << language.printStatement("definition", "${nestedType.name}.${property.name}", accessor)
                    } else {
                        lines << language.printStatement("definition", "${nestedType.name}.${property.name}",
                            generatePropertyAccess(parentAccessor, property, language))
                    }
                }
            }
        }
        if (dependenciesDeclaration) {
            dependenciesDeclaration.collectors.each { collectorName ->
                def accessor = "definition.printDependencies(${language.propertyAccessor('definition', 'dependencies')}.get${JavaSources.capitalize(collectorName)}())"
                lines << language.printStatement("definition", collectorName, accessor)
            }
        }
        if (shape == TypeShape.ABSTRACT_CLASS && showsConfigureInvocations) {
            nestedTypes.findAll { nested ->
                nested.kind == NestedKind.PLAIN &&
                    NestedRenderer.effectiveShapeOf(nested, this.shape) == TypeShape.ABSTRACT_CLASS
            }.each { nestedType ->
                def methodCall = "definition.maybe${JavaSources.capitalize(nestedType.name)}Configured()"
                lines << language.printCall("\"definition \" + ${methodCall}")
            }
        }
        return lines.join("\n")
    }

    /**
     * Returns code that prints all build model property values, formatted for the specified language.
     * Used inside the plugin's task body for test verification.
     */
    String displayModelPropertyValues(Language language) {
        if (buildModel == null) {
            return ""
        }
        def lines = []
        buildModel.properties.each { property ->
            lines << language.printStatement("model", property.name, generatePropertyAccess("model", property, language))
        }
        return lines.join("\n")
    }

    // --- Private helpers ---

    private String generateInterfaceContent(String effectiveClassName) {
        def extendsClause
        if (buildModel != null) {
            extendsClause = "extends ${Definition.class.simpleName}<${effectiveClassName}.${buildModel.className}>"
        } else if (usesNoBuildModel) {
            extendsClause = "extends ${Definition.class.simpleName}<${BuildModel.class.simpleName}.None>"
        } else {
            extendsClause = ""
        }

        def hiddenInDefinitionImport = dependenciesDeclaration != null ? "import ${HiddenInDefinition.class.name};" : ""

        return """
            package ${packageName};

            ${hiddenInDefinitionImport}

            import org.gradle.api.Action;
            import org.gradle.api.Named;
            import org.gradle.api.NamedDomainObjectContainer;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.file.DirectoryProperty;
            import org.gradle.api.file.RegularFileProperty;
            import org.gradle.api.file.Directory;
            import org.gradle.api.file.RegularFile;
            import org.gradle.api.tasks.Nested;
            import ${Definition.class.name};
            import ${BuildModel.class.name};

            import javax.inject.Inject;

            public interface ${effectiveClassName} ${extendsClause} {
                ${generatePropertyDeclarations(false)}

                ${JavaSources.generateInjectedServiceDeclarations(injectedServices, false)}

                ${generateNestedTypeDeclarations(false)}

                ${NestedRenderer.renderNestedBodies(nestedTypes, TypeShape.INTERFACE)}

                ${generateBuildModelInterface()}
            }
        """
    }

    private String generateAbstractClassContent(String effectiveClassName) {
        def implementsClause = buildModel != null
            ? "implements ${Definition.class.simpleName}<${effectiveClassName}.${buildModel.className}>"
            : ""

        def hiddenInDefinitionImport = dependenciesDeclaration != null ? "import ${HiddenInDefinition.class.name};" : ""

        return """
            package ${packageName};

            ${hiddenInDefinitionImport}
            import org.gradle.declarative.dsl.model.annotations.Adding;

            import org.gradle.api.Action;
            import org.gradle.api.Named;
            import org.gradle.api.NamedDomainObjectContainer;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.file.DirectoryProperty;
            import org.gradle.api.file.RegularFileProperty;
            import org.gradle.api.file.Directory;
            import org.gradle.api.file.RegularFile;
            import org.gradle.api.tasks.Nested;
            import ${Definition.class.name};
            import ${BuildModel.class.name};

            import javax.inject.Inject;

            public abstract class ${effectiveClassName} ${implementsClause} {
                ${generateAbstractClassFields()}

                ${generateAbstractClassConstructor(effectiveClassName)}

                ${generatePropertyDeclarations(true)}

                ${JavaSources.generateAddingMethods(properties)}

                ${generateAbstractNestedGetters()}

                ${generateDependenciesMembers()}

                ${JavaSources.generateInjectedServiceDeclarations(injectedServices, true)}

                ${NestedRenderer.renderNestedBodies(nestedTypes, TypeShape.ABSTRACT_CLASS)}

                ${generateAbstractClassMethods()}

                ${generateBuildModelInterface()}
            }
        """
    }

    private String generateChildInterfaceContent(String effectiveClassName) {
        def parentClassName = "Parent${effectiveClassName}"
        return """
            package ${packageName};

            import org.gradle.api.Action;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;

            import javax.inject.Inject;

            public interface ${effectiveClassName} extends ${parentClassName} { }
        """
    }

    private String getParentClassContent(String parentClassName) {
        // Generate the parent using the standard interface template but with the parent's injected services
        def parentBuilder = new DefinitionBuilder(parentClassName)
        parentBuilder.packageName = packageName
        parentBuilder.buildModel = buildModel
        parentBuilder.properties = properties
        parentBuilder.injectedServices = parentDefinition.injectedServices
        parentBuilder.nestedTypes = nestedTypes
        return parentBuilder.generateInterfaceContent(parentClassName)
    }

    private String generatePropertyDeclarations(boolean isAbstract) {
        def lines = []
        properties.each { property ->
            lines << generatePropertyGetter(property, isAbstract)
        }
        return lines.join("\n\n")
    }

    private String generatePropertyGetter(PropertyDeclaration property, boolean isAbstract) {
        def returnType = JavaSources.getPropertyReturnType(property)
        def prefix = isAbstract ? "public abstract " : ""
        def getterName = "get${JavaSources.capitalize(property.name)}"

        if (property.kind == PropertyKind.JAVA_BEAN) {
            if (property.javaBeanData().style == JavaBeanStyle.CONCRETE) {
                return """private ${property.type.simpleName} ${property.name};

                ${JavaSources.renderAnnotations(property.allAnnotations, '                ')}public ${property.type.simpleName} ${getterName}() {
                    return ${property.name};
                }

                public void set${JavaSources.capitalize(property.name)}(${property.type.simpleName} value) {
                    this.${property.name} = value;
                }"""
            }
            def setter = isAbstract ? "public abstract void set${JavaSources.capitalize(property.name)}(${property.type.simpleName} value);" : "void set${JavaSources.capitalize(property.name)}(${property.type.simpleName} value);"
            return """${JavaSources.renderAnnotations(property.allAnnotations, '                ')}${prefix}${property.type.simpleName} ${getterName}();
                ${setter}"""
        }

        if (JavaSources.needsBackingProperty(property)) {
            return """${JavaSources.renderAnnotations(property.allAnnotations, '                ')}public ${returnType} ${getterName}() {
                    return ${property.name};
                }"""
        }

        return "${JavaSources.renderAnnotations(property.allAnnotations, '                ')}${prefix}${returnType} ${getterName}();"
    }

    private String generateNestedTypeDeclarations(boolean isAbstract) {
        def lines = []
        nestedTypes.each { nestedType ->
            if (nestedType.kind == NestedKind.NDOC) {
                if (nestedType.ndocData().outProjected) {
                    // Private getter + public out-projected getter
                    lines << "${JavaSources.renderAnnotations(nestedType.allAnnotations, '                ')}abstract NamedDomainObjectContainer<${nestedType.typeName}> get${JavaSources.capitalize(nestedType.name)}();"
                    lines << "public NamedDomainObjectContainer<? extends ${nestedType.typeName}> getOut${JavaSources.capitalize(nestedType.name)}() { return get${JavaSources.capitalize(nestedType.name)}(); };"
                } else {
                    lines << "${JavaSources.renderAnnotations(nestedType.allAnnotations, '                ')}public abstract NamedDomainObjectContainer<${nestedType.typeName}> get${JavaSources.capitalize(nestedType.name)}();"
                }
            } else {
                def prefix = isAbstract ? "public abstract " : ""
                lines << """${JavaSources.renderAnnotations(nestedType.allAnnotations, '                ')}@Nested
                ${prefix}${nestedType.typeName} get${JavaSources.capitalize(nestedType.name)}();"""
            }
        }
        return lines.join("\n\n")
    }

    private String generateBuildModelInterface() {
        if (buildModel == null) {
            return ""
        }
        def buildModelPropertyGetters = buildModel.properties.collect { property ->
            "${JavaSources.renderAnnotations(property.allAnnotations, '                ')}${JavaSources.getPropertyReturnType(property)} get${JavaSources.capitalize(property.name)}();"
        }.join("\n")

        def implInterface = ""
        if (buildModel.implementationClassName) {
            def implPropertyGetters = buildModel.properties.collect { property ->
                "${JavaSources.renderAnnotations(property.allAnnotations, '                    ')}${JavaSources.getPropertyReturnType(property)} get${JavaSources.capitalize(property.name)}();"
            }.join("\n")
            implInterface = """
                public interface ${buildModel.implementationClassName} extends ${buildModel.className} {
                    ${implPropertyGetters}
                }
            """
        }

        return """
            public interface ${buildModel.className} extends BuildModel {
                ${buildModelPropertyGetters}
            }
            ${implInterface}
        """
    }

    private String generateAbstractClassFields() {
        def lines = []
        nestedTypes.findAll { needsBackingField(it) }.each { nestedType ->
            lines << "private final ${nestedType.typeName} ${nestedType.name};"
            if (nestedType.kind == NestedKind.PLAIN && showsConfigureInvocations) {
                lines << "private boolean is${JavaSources.capitalize(nestedType.name)}Configured = false;"
            }
        }
        properties.findAll { JavaSources.needsBackingProperty(it) }.each { property ->
            lines << "private final Property<${property.type.simpleName}> ${property.name};"
        }
        return lines.join("\n")
    }

    private String generateAbstractClassConstructor(String effectiveClassName) {
        def hasNestedTypes = nestedTypes.any { needsBackingField(it) }
        def hasPropertyFields = properties.any { JavaSources.needsBackingProperty(it) }
        if (hasNestedTypes || hasPropertyFields) {
            def needsObjectFactory = hasNestedTypes || hasPropertyFields
            def params = needsObjectFactory ? "ObjectFactory objects" : ""
            def inits = []
            nestedTypes.findAll { needsBackingField(it) }.each {
                inits << "this.${it.name} = objects.newInstance(${it.typeName}.class);"
                if (it.kind == NestedKind.UNDISCOVERABLE && it.undiscoverableData().initializationCode) {
                    inits << it.undiscoverableData().initializationCode
                }
            }
            properties.findAll { JavaSources.needsBackingProperty(it) }.each {
                inits << "this.${it.name} = ${JavaSources.propertyFieldInitializer(it)};"
            }
            return """
                @Inject
                public ${effectiveClassName}(${params}) {
                    ${inits.join("\n")}
                }
            """
        }
        def hasConcreteJavaBeans = properties.any { it.kind == PropertyKind.JAVA_BEAN && it.javaBeanData().style == JavaBeanStyle.CONCRETE }
        if (hasConcreteJavaBeans) {
            return """
                @Inject
                public ${effectiveClassName}() {
                }
            """
        }
        return ""
    }

    private String generateAbstractNestedGetters() {
        def lines = []
        nestedTypes.each { nestedType ->
            if (nestedType.kind == NestedKind.NDOC) {
                if (nestedType.ndocData().outProjected) {
                    lines << "${JavaSources.renderAnnotations(nestedType.allAnnotations, '                ')}abstract NamedDomainObjectContainer<${nestedType.typeName}> get${JavaSources.capitalize(nestedType.name)}();"
                    lines << "public NamedDomainObjectContainer<? extends ${nestedType.typeName}> getOut${JavaSources.capitalize(nestedType.name)}() { return get${JavaSources.capitalize(nestedType.name)}(); };"
                } else {
                    lines << "${JavaSources.renderAnnotations(nestedType.allAnnotations, '                ')}public abstract NamedDomainObjectContainer<${nestedType.typeName}> get${JavaSources.capitalize(nestedType.name)}();"
                }
            } else if (nestedType.kind == NestedKind.UNDISCOVERABLE) {
                // The Action-taking method is the sole DCL schema entry point for undiscoverable
                // types: there is intentionally no public getter, so the declarative engine can
                // only navigate into the inner object through this method.
                lines << """
                public void ${nestedType.name}(Action<? super ${nestedType.typeName}> action) {
                    action.execute(${nestedType.name});
                }
                """
            } else if (needsBackingField(nestedType)) {
                // Field-backed concrete getter: used for shared-ref (always) and for
                // effectively-ABSTRACT_CLASS regular nested types. Carries the optional
                // configure-invocations side effect.
                def sideEffect = showsConfigureInvocations && nestedType.kind == NestedKind.PLAIN
                    ? "is${JavaSources.capitalize(nestedType.name)}Configured = true; // TODO: get rid of the side effect in the getter"
                    : ""
                lines << """
                ${JavaSources.renderAnnotations(nestedType.allAnnotations, '                ')}public ${nestedType.typeName} get${JavaSources.capitalize(nestedType.name)}() {
                    ${sideEffect}
                    return ${nestedType.name};
                }
                """
            } else {
                // Effectively-INTERFACE regular nested under an abstract-class outer:
                // no field, Gradle's ObjectFactory synthesizes the instance for the @Nested
                // abstract getter. Cannot carry side effects, enforced at build() time.
                lines << """${JavaSources.renderAnnotations(nestedType.allAnnotations, '                ')}@Nested
                public abstract ${nestedType.typeName} get${JavaSources.capitalize(nestedType.name)}();"""
            }
        }
        return lines.join("\n")
    }

    private String getDependenciesInterfaceContent() {
        if (!dependenciesDeclaration) {
            return ""
        }
        def collectorGetters = dependenciesDeclaration.collectors.collect { name ->
            "DependencyCollector get${JavaSources.capitalize(name)}();"
        }.join("\n\n")
        return """
            package ${packageName};

            import org.gradle.api.artifacts.dsl.Dependencies;
            import org.gradle.api.artifacts.dsl.DependencyCollector;

            public interface ${dependenciesDeclaration.interfaceName} extends Dependencies {
                ${collectorGetters}
            }
        """
    }

    private String generateDependenciesMembers() {
        if (!dependenciesDeclaration) {
            return ""
        }
        return """
            @Nested
            public abstract ${dependenciesDeclaration.interfaceName} getDependencies();

            @${HiddenInDefinition.class.simpleName}
            public void dependencies(Action<? super ${dependenciesDeclaration.interfaceName}> action) {
                action.execute(getDependencies());
            }

            public String printDependencies(org.gradle.api.artifacts.dsl.DependencyCollector collector) {
                return collector.getDependencies().get().stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", "));
            }

            public String printList(java.util.List<?> list) {
                return list.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", "));
            }
        """
    }

    private String generateAbstractClassMethods() {
        if (!showsConfigureInvocations) {
            return ""
        }
        // The helper reads the is${X}Configured field, which is only emitted for
        // effectively-ABSTRACT_CLASS regular nested. validateShapes() rejects mixes, so
        // this filter is belt-and-braces — it also documents the contract in-place.
        def lines = []
        nestedTypes.findAll { nested ->
            nested.kind == NestedKind.PLAIN &&
                NestedRenderer.effectiveShapeOf(nested, this.shape) == TypeShape.ABSTRACT_CLASS
        }.each { nestedType ->
            lines << """
                public String maybe${JavaSources.capitalize(nestedType.name)}Configured() {
                    return is${JavaSources.capitalize(nestedType.name)}Configured ? "(${nestedType.name} is configured)" : "";
                }
            """
        }
        return lines.join("\n")
    }

    private String generateImplConstructorAndFields() {
        def nestedFields = nestedTypes.findAll { it.kind != NestedKind.NDOC }
        if (nestedFields.isEmpty()) {
            return ""
        }
        def fields = nestedFields.collect { "private final ${it.typeName} ${it.name};" }.join("\n")
        def inits = nestedFields.collect { "this.${it.name} = objects.newInstance(${it.typeName}.class);" }.join("\n")
        def getters = nestedFields.collect {
            """${JavaSources.renderAnnotations(it.allAnnotations, '                ')}@Override
                public ${it.typeName} get${JavaSources.capitalize(it.name)}() {
                    return ${it.name};
                }"""
        }.join("\n\n")

        return """
            ${fields}

            @Inject
            public ${implementationClassName}(ObjectFactory objects) {
                ${inits}
            }

            ${getters}
        """
    }

    private String generatePropertyMapping(PropertyDeclaration buildModelProperty, PropertyDeclaration definitionProperty, Language language) {
        def modelAccessor = language.propertyAccessor("model", buildModelProperty.name)
        def definitionAccessor = language.propertyAccessor("definition", definitionProperty.name)
        return "${modelAccessor}.set(${definitionAccessor})${language.statementEnd()}"
    }

    private static String generatePropertyAccess(String objectExpression, PropertyDeclaration property, Language language) {
        def accessor = language.propertyAccessor(objectExpression, property.name)
        if (property.kind == PropertyKind.SHARED_REF) {
            // Direct nested-object reference; no .get()/.getOrNull() unwrap.
            return accessor
        }
        if (property.kind == PropertyKind.READ_ONLY || property.kind == PropertyKind.JAVA_BEAN) {
            if (property.type == Directory || property.type == RegularFile) {
                return "${accessor}${language.asFileExpression()}"
            }
            if (property.kind == PropertyKind.READ_ONLY) {
                return accessor
            }
        }
        if (property.kind == PropertyKind.LIST_PROPERTY) {
            return "${accessor}.get()"
        }
        if (property.type == DirectoryProperty || property.type == RegularFileProperty) {
            return "${accessor}.get()${language.asFileExpression()}"
        }
        if (property.type == Directory || property.type == RegularFile) {
            return "${accessor}.get()${language.asFileExpression()}"
        }
        return "${accessor}.getOrNull()"
    }
}
