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
import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition
import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.Definition
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
 *     property("foo", "Foo") {
 *         implementsDefinition("FooBuildModel") { property "barProcessed", String }
 *         property "bar", String
 *     }
 * }
 * </pre>
 */
class DefinitionBuilder {
    /** Controls whether the generated definition is an interface or an abstract class. */
    enum Shape {
        /** Generates a Java interface. This is the default. */
        INTERFACE,
        /** Generates a Java abstract class with constructor injection and concrete nested type fields. */
        ABSTRACT_CLASS
    }

    /** The simple class name of the definition (e.g. "TestProjectTypeDefinition"). */
    String className

    /** The Java package for generated source files. */
    String packageName = "org.gradle.test"

    /** Whether to generate an interface or abstract class. */
    Shape shape = Shape.INTERFACE

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
     * <p>Silently no-op on {@link Shape#INTERFACE} definitions: the underlying emit
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
     * Configures the existing {@code BuildModel} inner interface (e.g. to add properties).
     * The build model must already exist (set by the factory method or a prior call).
     *
     * @param config closure to configure the build model
     */
    void buildModel(
        @DelegatesTo(value = BuildModelDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config
    ) {
        if (this.buildModel == null) {
            throw new IllegalStateException("No build model exists. Use buildModel(String, Closure) to create one first.")
        }
        config.delegate = this.buildModel
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
    }

    /**
     * Declares a {@code BuildModel} inner interface on this definition with the given class name.
     *
     * @param modelClassName the simple class name (e.g. "ModelType")
     * @param config optional closure to add properties to the build model
     */
    void buildModel(String modelClassName,
        @DelegatesTo(value = BuildModelDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config = {}
    ) {
        this.buildModel = new BuildModelDeclaration(className: modelClassName)
        config.delegate = this.buildModel
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
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
     * non-undiscoverable nested type. Off by default. Silently no-op on {@link Shape#INTERFACE}.
     * See {@link #showsConfigureInvocations}.
     */
    void showConfigureInvocations() {
        this.showsConfigureInvocations = true
    }

    /** Adds a {@code Property<T>} getter to the definition. */
    void property(String name, Class type) {
        properties.add(new PropertyDeclaration(name: name, type: type))
    }

    /** Adds a {@code Property<T>} getter with optional configuration (e.g. annotations). */
    void property(String name, Class type,
        @DelegatesTo(value = PropertyDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config
    ) {
        def property = new PropertyDeclaration(name: name, type: type)
        config.delegate = property
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        properties.add(property)
    }

    /** Adds a read-only property that returns the concrete type directly (e.g. {@code Directory getDir()}). */
    void readOnlyProperty(String name, Class type) {
        properties.add(new PropertyDeclaration(name: name, type: type, isReadOnly: true))
    }

    /** Adds a Java Bean property with getter/setter pair instead of {@code Property<T>}. */
    void javaBeanProperty(String name, Class type) {
        properties.add(new PropertyDeclaration(name: name, type: type, isJavaBean: true))
    }

    /** Adds a Java Bean property with getter/setter pair, with optional configuration (e.g. shape). */
    void javaBeanProperty(String name, Class type,
        @DelegatesTo(value = PropertyDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config
    ) {
        def property = new PropertyDeclaration(name: name, type: type, isJavaBean: true)
        config.delegate = property
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        properties.add(property)
    }

    /** Adds a {@code ListProperty<T>} getter to the definition. */
    void listProperty(String name, Class elementType) {
        properties.add(new PropertyDeclaration(name: name, type: elementType, isList: true))
    }

    /** Adds a {@code ListProperty<T>} getter with optional configuration (e.g. annotations). */
    void listProperty(String name, Class elementType,
        @DelegatesTo(value = PropertyDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config
    ) {
        def property = new PropertyDeclaration(name: name, type: elementType, isList: true)
        config.delegate = property
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        properties.add(property)
    }

    /**
     * Adds a {@code @Nested} type with its own properties, services, and sub-nested types.
     *
     * @param name the accessor name (e.g. "foo" generates {@code getFoo()} and {@code foo(Action)})
     * @param nestedTypeName the type name of the nested interface/class (e.g. "Foo")
     * @param config closure to configure the nested type's properties
     */
    void property(String name, String nestedTypeName,
        @DelegatesTo(value = PropertyTypeDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config = {}
    ) {
        def nestedType = new PropertyTypeDeclaration(name: name, typeName: nestedTypeName)
        config.delegate = nestedType
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        nestedTypes.add(nestedType)
    }

    /**
     * Adds a property whose type is a previously declared shared type (see
     * {@code TestScenarioBuilder.sharedType(...)}).
     *
     * <p>Emits a {@code @Nested} getter returning the shared type's class directly (no
     * {@code Property<T>} wrapping). The shared type's body is NOT re-emitted inline; it
     * lives in its own top-level file produced by {@link SharedTypeBuilder}.</p>
     *
     * @param name the accessor name on this definition
     * @param ref the shared-type reference returned by {@code TestScenarioBuilder.sharedType(...)}
     */
    void property(String name, PropertyTypeDeclaration ref) {
        def synthesized = new PropertyTypeDeclaration(
            name: name,
            typeName: ref.typeName,
            properties: ref.properties,
            nestedTypes: ref.nestedTypes,
            injectedServices: ref.injectedServices,
            implementsDefinition: ref.implementsDefinition,
            buildModel: ref.buildModel,
            isSharedRef: true
        )
        nestedTypes.add(synthesized)
    }

    /**
     * Adds a {@code NamedDomainObjectContainer<T>} property to the definition.
     *
     * @param name the accessor name (e.g. "sources" generates {@code getSources()})
     * @param elementTypeName the element type name (e.g. "Source")
     * @param config closure to configure the NDOC element type
     */
    void ndoc(String name, String elementTypeName,
        @DelegatesTo(value = PropertyTypeDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config = {}
    ) {
        def nestedType = new PropertyTypeDeclaration(name: name, typeName: elementTypeName, isNdoc: true)
        config.delegate = nestedType
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        nestedTypes.add(nestedType)
    }

    /**
     * Adds an <em>undiscoverable</em> nested type to the definition: an inner object that Gradle's
     * property machinery cannot auto-discover. The enclosing definition owns the instance as a
     * {@code private final} field, initialized in the constructor via {@code ObjectFactory}. No
     * {@code @Nested} annotation is emitted and <strong>no public getter is generated</strong>;
     * the only entry point is the generated {@code foo(Action)} method, which executes the
     * action against the field. Optional initialization code supplied via
     * {@link PropertyTypeDeclaration#initializeWith(String)} is inlined into the enclosing constructor
     * immediately after the field is created.
     *
     * <p>Requires the definition to use {@link Shape#ABSTRACT_CLASS}. Interface-shaped definitions
     * cannot declare undiscoverable properties; this method will fail if the shape is
     * {@link Shape#INTERFACE}. Because {@code parentDefinition} definitions render via the
     * interface path (for both the parent and the empty child), declaring undiscoverable in a
     * parent/child setup is also rejected by this same guard.</p>
     *
     * <p>The inner type is always a Java {@code interface} (not an abstract static class). It may
     * optionally extend {@code Definition<Foo.FooBuildModel>} via {@code implementsDefinition(...)}
     * inside the configuration closure. Nesting another {@code undiscoverable(...)} inside this
     * closure is not supported — the inner type is a pure interface and has no constructor in which
     * to initialize a sub-field.</p>
     *
     * @param name the accessor name (e.g. "foo" generates the {@code foo(Action)} DSL method)
     * @param typeName the inner interface name (e.g. "Foo")
     * @param config closure to configure the undiscoverable type's properties
     */
    void undiscoverable(String name, String typeName,
        @DelegatesTo(value = PropertyTypeDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config = {}
    ) {
        if (shape == Shape.INTERFACE) {
            throw new IllegalStateException(
                "undiscoverable(...) requires ABSTRACT_CLASS shape; set shape(ABSTRACT_CLASS) before declaring undiscoverable properties."
            )
        }
        def nestedType = new PropertyTypeDeclaration(name: name, typeName: typeName, isUndiscoverable: true)
        config.delegate = nestedType
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        if (!nestedType.allAnnotations.isEmpty()) {
            throw new IllegalStateException(
                "annotations(...) cannot be used on an undiscoverable declaration; it has no public getter."
            )
        }
        nestedTypes.add(nestedType)
    }

    /** Adds an {@code @Inject} service accessor to the definition. */
    void injectedService(String name, Class type) {
        injectedServices.add(new ServiceDeclaration(name: name, type: type))
    }

    /** Sets the shape (interface or abstract class) of the generated definition. */
    void shape(Shape s) { this.shape = s }

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
        this.dependenciesDeclaration = new DependenciesDeclaration()
        config.delegate = this.dependenciesDeclaration
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
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
        def parent = new DefinitionBuilder()
        config.delegate = parent
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        this.parentDefinition = parent
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
        if (shape == Shape.ABSTRACT_CLASS) {
            return generateAbstractClassContent(className)
        }
        if (parentDefinition) {
            return generateChildInterfaceContent(className)
        }
        return generateInterfaceContent(className)
    }

    /**
     * Resolves the effective body shape for a nested type. An explicit shape on the
     * nested wins; otherwise the nested inherits from the enclosing context (the outer
     * definition's shape for top-level nested, or the parent nested's effective shape
     * for sub-nested).
     */
    static Shape effectiveShapeOf(PropertyTypeDeclaration nested, Shape enclosingShape) {
        return nested.shape ?: enclosingShape
    }

    /**
     * Returns true if the outer abstract class needs a backing field (and corresponding
     * constructor initialization) for this nested type. NDOC never uses field-backed
     * storage. Undiscoverable and shared-ref always do (their access patterns require it).
     * Regular nested types use field-backed storage only when their effective shape is
     * {@link Shape#ABSTRACT_CLASS}.
     */
    private boolean needsBackingField(PropertyTypeDeclaration nested) {
        if (nested.isNdoc) {
            return false
        }
        if (nested.isUndiscoverable || nested.isSharedRef) {
            return true
        }
        return effectiveShapeOf(nested, this.shape) == Shape.ABSTRACT_CLASS
    }

    /**
     * Validates cross-cutting constraints on nested-type shapes. Called from {@link #build}
     * before any emission. Currently enforces:
     * <ul>
     *     <li>{@link #showsConfigureInvocations} requires every regular top-level nested type
     *         to be effectively {@link Shape#ABSTRACT_CLASS}, because the side-effect
     *         scaffolding is emitted into the concrete getter path only.</li>
     * </ul>
     */
    private void validateShapes() {
        if (shape == Shape.ABSTRACT_CLASS && showsConfigureInvocations) {
            def offenders = nestedTypes.findAll { nested ->
                !nested.isNdoc && !nested.isUndiscoverable && !nested.isSharedRef &&
                    effectiveShapeOf(nested, this.shape) == Shape.INTERFACE
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
        def mappableProperties = properties.findAll { !it.isList }
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
            if (buildModelProperty.sharedTypeRef != null) {
                // Shared-ref on the build model: auto-map only if the definition has a matching
                // shared-ref entry by the same accessor name and the shared type is not itself
                // definition-shaped (which would require context.getBuildModel, punted).
                def matchingRef = nestedTypes.find { it.isSharedRef && it.name == buildModelProperty.name }
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
        def statementEnd = (language == Language.KOTLIN) ? "" : ";"
        def defExpr = propertyAccessor("definition", accessorName, language)
        def modelExpr = propertyAccessor("model", accessorName, language)
        return ref.properties.findAll { !it.isList && !it.isReadOnly && !it.isJavaBean }.collect { scalar ->
            def defAccess = propertyAccessor(defExpr, scalar.name, language)
            def modelAccess = propertyAccessor(modelExpr, scalar.name, language)
            "${modelAccess}.set(${defAccess})${statementEnd}"
        }.join("\n")
    }

    private String generateNestedDefinitionMapping(PropertyTypeDeclaration nestedType, Language language) {
        if (nestedType.buildModel.customMappings.containsKey(language)) {
            return nestedType.buildModel.customMappings[language]
        }
        if (nestedType.isUndiscoverable) {
            // Auto-derived mappings would need definition.getFoo(), but undiscoverable
            // nested types have no public getter. Custom mappings are handled above.
            return ""
        }
        if (nestedType.isSharedRef) {
            // Shared types are not registered via feature binding, so context.getBuildModel(...)
            // would not resolve at runtime. Shared-ref ↔ shared-ref auto-mapping happens in
            // generateTopLevelBuildModelMapping via generateSharedRefMapping; otherwise rely on
            // a custom mapping(...) block on the referring build model.
            return ""
        }
        def elementExpression
        if (nestedType.isNdoc) {
            elementExpression = decapitalize(nestedType.typeName)
        } else {
            elementExpression = propertyAccessor("definition", nestedType.name, language)
        }
        def modelExpression = "context.getBuildModel(${elementExpression})"
        def statementEnd = (language == Language.KOTLIN) ? "" : ";"
        def mappings = []
        nestedType.buildModel.properties.findAll { !it.isList }.each { bmProp ->
            def defProp = nestedType.properties.find { it.name == bmProp.name && !it.isList }
            if (defProp) {
                def modelAccess = propertyAccessor(modelExpression, bmProp.name, language)
                def defAccess = propertyAccessor(elementExpression, defProp.name, language)
                mappings << "${modelAccess}.set(${defAccess})${statementEnd}"
            }
        }
        if (mappings.isEmpty()) {
            return ""
        }
        if (!nestedType.isNdoc) {
            return mappings.join("\n")
        }
        def containerAccessor = propertyAccessor("definition", nestedType.name, language)
        def body = mappings.join("\n    ")
        if (language == Language.KOTLIN) {
            return "${containerAccessor}.configureEach { ${elementExpression} ->\n    ${body}\n}"
        }
        return "${containerAccessor}.configureEach(${elementExpression} -> {\n    ${body}\n});"
    }

    private static String decapitalize(String name) {
        return name.length() == 1 ? name.toLowerCase() : name[0].toLowerCase() + name[1..-1]
    }

    /**
     * Returns code that prints all definition property values, formatted for the specified language.
     * Used inside the plugin's task body for test verification.
     */
    String displayDefinitionPropertyValues(Language language) {
        def lines = []
        properties.each { property ->
            if (property.isList && dependenciesDeclaration) {
                def accessor = "definition.printList(${propertyAccessor('definition', property.name, language)}.get())"
                lines << printStatement("definition", property.name, accessor, language)
            } else {
                lines << printStatement("definition", property.name, generatePropertyAccess("definition", property, language), language)
            }
        }
        nestedTypes.each { nestedType ->
            if (nestedType.isUndiscoverable) {
                // Undiscoverable types have no public getter, so their properties cannot be
                // accessed from the task body for display.
                return
            }
            if (nestedType.isNdoc) {
                def accessor = "${propertyAccessor('definition', nestedType.name, language)}.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(\", \"))"
                lines << printStatement("definition", nestedType.name, accessor, language)
            } else {
                nestedType.properties.each { property ->
                    def parentAccessor = propertyAccessor("definition", nestedType.name, language)
                    if (property.isList && dependenciesDeclaration) {
                        def accessor = "definition.printList(${parentAccessor}.get${capitalize(property.name)}().get())"
                        lines << printStatement("definition", "${nestedType.name}.${property.name}", accessor, language)
                    } else {
                        lines << printStatement("definition", "${nestedType.name}.${property.name}",
                            generatePropertyAccess(parentAccessor, property, language), language)
                    }
                }
            }
        }
        if (dependenciesDeclaration) {
            dependenciesDeclaration.collectors.each { collectorName ->
                def accessor = "definition.printDependencies(${propertyAccessor('definition', 'dependencies', language)}.get${capitalize(collectorName)}())"
                lines << printStatement("definition", collectorName, accessor, language)
            }
        }
        if (shape == Shape.ABSTRACT_CLASS && showsConfigureInvocations) {
            nestedTypes.findAll { nested ->
                !nested.isNdoc && !nested.isUndiscoverable && !nested.isSharedRef &&
                    effectiveShapeOf(nested, this.shape) == Shape.ABSTRACT_CLASS
            }.each { nestedType ->
                def methodCall = "definition.maybe${capitalize(nestedType.name)}Configured()"
                lines << printCall("\"definition \" + ${methodCall}", language)
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
            lines << printStatement("model", property.name, generatePropertyAccess("model", property, language), language)
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

                ${generateInjectedServiceDeclarations(injectedServices, false)}

                ${generateNestedTypeDeclarations(false)}

                ${renderNestedBodies(nestedTypes, Shape.INTERFACE)}

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
            import ${Adding.class.name};

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

                ${generateAddingMethods(properties)}

                ${generateAbstractNestedGetters()}

                ${generateDependenciesMembers()}

                ${generateInjectedServiceDeclarations(injectedServices, true)}

                ${renderNestedBodies(nestedTypes, Shape.ABSTRACT_CLASS)}

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
        def returnType = getPropertyReturnType(property)
        def prefix = isAbstract ? "public abstract " : ""
        def getterName = "get${capitalize(property.name)}"

        if (property.isJavaBean) {
            if (property.shape == PropertyDeclaration.Shape.CONCRETE) {
                return """private ${property.type.simpleName} ${property.name};

                ${renderAnnotations(property.allAnnotations, '                ')}public ${property.type.simpleName} ${getterName}() {
                    return ${property.name};
                }

                public void set${capitalize(property.name)}(${property.type.simpleName} value) {
                    this.${property.name} = value;
                }"""
            }
            def setter = isAbstract ? "public abstract void set${capitalize(property.name)}(${property.type.simpleName} value);" : "void set${capitalize(property.name)}(${property.type.simpleName} value);"
            return """${renderAnnotations(property.allAnnotations, '                ')}${prefix}${property.type.simpleName} ${getterName}();
                ${setter}"""
        }

        if (property.isPropertyFieldType()) {
            return """${renderAnnotations(property.allAnnotations, '                ')}public ${returnType} ${getterName}() {
                    return ${property.name};
                }"""
        }

        return "${renderAnnotations(property.allAnnotations, '                ')}${prefix}${returnType} ${getterName}();"
    }

    private String generateNestedTypeDeclarations(boolean isAbstract) {
        def lines = []
        nestedTypes.each { nestedType ->
            if (nestedType.isNdoc) {
                if (nestedType.isOutProjected) {
                    // Private getter + public out-projected getter
                    lines << "${renderAnnotations(nestedType.allAnnotations, '                ')}abstract NamedDomainObjectContainer<${nestedType.typeName}> get${capitalize(nestedType.name)}();"
                    lines << "public NamedDomainObjectContainer<? extends ${nestedType.typeName}> getOut${capitalize(nestedType.name)}() { return get${capitalize(nestedType.name)}(); };"
                } else {
                    lines << "${renderAnnotations(nestedType.allAnnotations, '                ')}public abstract NamedDomainObjectContainer<${nestedType.typeName}> get${capitalize(nestedType.name)}();"
                }
            } else {
                def prefix = isAbstract ? "public abstract " : ""
                lines << """${renderAnnotations(nestedType.allAnnotations, '                ')}@Nested
                ${prefix}${nestedType.typeName} get${capitalize(nestedType.name)}();"""
            }
        }
        return lines.join("\n\n")
    }

    /**
     * Renders the bodies of each nested type in {@code items}, joined by blank lines.
     * Each body respects the nested type's effective shape (explicit {@code shape}
     * override, or fallback to {@code enclosingEffective}). NDOC, undiscoverable, and
     * shared-ref use their fixed rendering regardless. Sibling build-model interfaces
     * are appended after each non-NDOC body that has one. Shared-ref entries are
     * skipped; their type body is produced by {@link SharedTypeBuilder}.
     */
    static String renderNestedBodies(List<PropertyTypeDeclaration> items, Shape enclosingEffective) {
        items.collect { sub ->
            if (sub.isSharedRef) {
                return ""
            }
            String body
            if (sub.isNdoc) {
                body = sub.implementsDefinition
                    ? generateNdocDefinitionInterface(sub)
                    : generateNdocElementClass(sub)
            } else if (sub.isUndiscoverable) {
                body = generateUndiscoverableInterface(sub)
            } else {
                def subEff = effectiveShapeOf(sub, enclosingEffective)
                body = (subEff == Shape.ABSTRACT_CLASS)
                    ? generateNestedAbstractClassBody(sub, subEff)
                    : generateNestedInterfaceBody(sub, subEff)
            }
            if (sub.buildModel && !sub.isNdoc) {
                body += "\n" + generateNestedBuildModelInterface(sub)
            }
            return body
        }.findAll { it }.join("\n\n")
    }

    /**
     * Renders a regular nested type as a public interface body. Sub-nested bodies are
     * dispatched via {@link #renderNestedBodies} with enclosing effective shape
     * {@link Shape#INTERFACE}, so each sub inherits INTERFACE unless it overrides.
     */
    static String generateNestedInterfaceBody(PropertyTypeDeclaration nested, Shape enclosingEffective) {
        def extendsClause = ""
        if (nested.implementsDefinition && nested.buildModel) {
            extendsClause = "extends ${Definition.class.simpleName}<${nested.buildModel.className}>"
        }

        def propertyGetters = nested.properties.collect { property ->
            "${renderAnnotations(property.allAnnotations, '                ')}public abstract ${getPropertyReturnType(property)} get${capitalize(property.name)}();"
        }.join("\n")

        def services = generateInjectedServiceDeclarations(nested.injectedServices, false)

        def nestedAccessors = nested.nestedTypes.collect { sub ->
            renderSubAccessor(sub, false)
        }.join("\n\n")

        def nestedBodies = renderNestedBodies(nested.nestedTypes, Shape.INTERFACE)

        return """
            public interface ${nested.typeName} ${extendsClause} {
                ${services}
                ${propertyGetters}
                ${nestedAccessors}
                ${nestedBodies}
            }
        """
    }

    /**
     * Renders a regular nested type as a public abstract static class body. Sub-nested
     * accessors use {@code @Nested public abstract} form. Sub-nested bodies are dispatched
     * via {@link #renderNestedBodies} with enclosing effective shape {@link Shape#ABSTRACT_CLASS}.
     */
    static String generateNestedAbstractClassBody(PropertyTypeDeclaration nested, Shape enclosingEffective) {
        def implementsClause = nested.implementsDefinition && nested.buildModel
            ? "implements ${Definition.class.simpleName}<${nested.buildModel.className}>"
            : ""

        def services = generateInjectedServiceDeclarations(nested.injectedServices, true)

        def propertyGetters = nested.properties.collect { property ->
            "${renderAnnotations(property.allAnnotations, '                ')}public abstract ${getPropertyReturnType(property)} get${capitalize(property.name)}();"
        }.join("\n")

        def addingMethods = generateAddingMethods(nested.properties)

        def nestedAccessors = nested.nestedTypes.collect { sub ->
            renderSubAccessor(sub, true)
        }.join("\n\n")

        def nestedBodies = renderNestedBodies(nested.nestedTypes, Shape.ABSTRACT_CLASS)

        return """
            public abstract static class ${nested.typeName} ${implementsClause} {
                public ${nested.typeName}() { }

                ${services}

                ${propertyGetters}

                ${addingMethods}

                ${nestedAccessors}

                ${nestedBodies}
            }
        """
    }

    /**
     * Renders an NDOC element that implements {@code Definition<BuildModel>}: an interface
     * body with an inner build-model interface (inner-qualified, unlike regular nested
     * build models which are siblings).
     */
    static String generateNdocDefinitionInterface(PropertyTypeDeclaration nested) {
        def extendsClause = nested.buildModel
            ? "extends ${Definition.class.simpleName}<${nested.typeName}.${nested.buildModel.className}>, Named"
            : ""
        def propertyGetters = nested.properties.collect { property ->
            "${renderAnnotations(property.allAnnotations, '                ')}public abstract ${getPropertyReturnType(property)} get${capitalize(property.name)}();"
        }.join("\n")
        def subNestedBodies = renderNestedBodies(nested.nestedTypes, Shape.INTERFACE)
        def bmIface = ""
        if (nested.buildModel) {
            def buildModelPropertyGetters = nested.buildModel.properties.collect { property ->
                "${renderAnnotations(property.allAnnotations, '                    ')}${getPropertyReturnType(property)} get${capitalize(property.name)}();"
            }.join("\n")
            bmIface = """
                public interface ${nested.buildModel.className} extends BuildModel {
                    ${buildModelPropertyGetters}
                }
                """
        }
        return """
            public interface ${nested.typeName} ${extendsClause} {
                ${propertyGetters}
                ${subNestedBodies}
                ${bmIface}
            }
            """
    }

    /**
     * Renders a sub-nested accessor inside a parent body. NDOC subs always use
     * {@code public abstract NamedDomainObjectContainer<...>}. Regular subs use
     * {@code @Nested public abstract Sub getSub();} when emitted inside an abstract class
     * body, or the unqualified interface-member form {@code @Nested Sub getSub();} inside
     * an interface body. Shared-ref entries emit no accessor (defensive: only top-level).
     */
    static String renderSubAccessor(PropertyTypeDeclaration sub, boolean inAbstractClass) {
        if (sub.isSharedRef) {
            return ""
        }
        if (sub.isNdoc) {
            return "${renderAnnotations(sub.allAnnotations, '                ')}public abstract NamedDomainObjectContainer<${sub.typeName}> get${capitalize(sub.name)}();"
        }
        def prefix = inAbstractClass ? "public abstract " : ""
        return """${renderAnnotations(sub.allAnnotations, '                ')}@Nested
                ${prefix}${sub.typeName} get${capitalize(sub.name)}();"""
    }

    static String generateNestedBuildModelInterface(PropertyTypeDeclaration nestedType) {
        if (!nestedType.buildModel) {
            return ""
        }
        def buildModelPropertyGetters = nestedType.buildModel.properties.collect { property ->
            "${renderAnnotations(property.allAnnotations, '                ')}${getPropertyReturnType(property)} get${capitalize(property.name)}();"
        }.join("\n")
        return """
            public interface ${nestedType.buildModel.className} extends BuildModel {
                ${buildModelPropertyGetters}
            }
        """
    }

    static String generateNdocElementClass(PropertyTypeDeclaration nestedType) {
        def propertyGetters = nestedType.properties.collect { property ->
            "${renderAnnotations(property.allAnnotations, '                ')}public abstract ${getPropertyReturnType(property)} get${capitalize(property.name)}();"
        }.join("\n")

        // NDOC element is itself an abstract static class; sub accessors use the abstract-class form.
        def nestedAccessors = nestedType.nestedTypes.collect { sub ->
            renderSubAccessor(sub, true)
        }.join("\n\n")

        // Sub bodies default to INTERFACE (preserves prior behavior where sub-nested inside
        // an NDOC element always rendered as interfaces); each sub can override via its own shape.
        def nestedInterfaces = renderNestedBodies(nestedType.nestedTypes, Shape.INTERFACE)

        return """
            public abstract static class ${nestedType.typeName} implements Named {
                private String name;

                public ${nestedType.typeName}(String name) {
                    this.name = name;
                }

                @Override
                public String getName() {
                    return name;
                }

                ${propertyGetters}

                ${nestedAccessors}

                ${nestedInterfaces}

                @Override
                public String toString() {
                    return "${nestedType.typeName}(name = " + name${nestedType.properties.collect { property -> " + \", ${property.name} = \" + get${capitalize(property.name)}().get()" }.join('')} + ")";
                }
            }
        """
    }

    private String generateBuildModelInterface() {
        if (buildModel == null) {
            return ""
        }
        def buildModelPropertyGetters = buildModel.properties.collect { property ->
            "${renderAnnotations(property.allAnnotations, '                ')}${getPropertyReturnType(property)} get${capitalize(property.name)}();"
        }.join("\n")

        def implInterface = ""
        if (buildModel.implementationClassName) {
            def implPropertyGetters = buildModel.properties.collect { property ->
                "${renderAnnotations(property.allAnnotations, '                    ')}${getPropertyReturnType(property)} get${capitalize(property.name)}();"
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
            if (!nestedType.isUndiscoverable && !nestedType.isSharedRef && showsConfigureInvocations) {
                lines << "private boolean is${capitalize(nestedType.name)}Configured = false;"
            }
        }
        properties.findAll { it.isPropertyFieldType() }.each { property ->
            lines << "private final Property<${property.type.simpleName}> ${property.name};"
        }
        return lines.join("\n")
    }

    private String generateAbstractClassConstructor(String effectiveClassName) {
        def hasNestedTypes = nestedTypes.any { needsBackingField(it) }
        def hasPropertyFields = properties.any { it.isPropertyFieldType() }
        if (hasNestedTypes || hasPropertyFields) {
            def needsObjectFactory = hasNestedTypes || hasPropertyFields
            def params = needsObjectFactory ? "ObjectFactory objects" : ""
            def inits = []
            nestedTypes.findAll { needsBackingField(it) }.each {
                inits << "this.${it.name} = objects.newInstance(${it.typeName}.class);"
                if (it.isUndiscoverable && it.initializationCode) {
                    inits << it.initializationCode
                }
            }
            properties.findAll { it.isPropertyFieldType() }.each {
                inits << "this.${it.name} = ${propertyFieldInitializer(it)};"
            }
            return """
                @Inject
                public ${effectiveClassName}(${params}) {
                    ${inits.join("\n")}
                }
            """
        }
        def hasConcreteJavaBeans = properties.any { it.isJavaBean && it.shape == PropertyDeclaration.Shape.CONCRETE }
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
            if (nestedType.isNdoc) {
                if (nestedType.isOutProjected) {
                    lines << "${renderAnnotations(nestedType.allAnnotations, '                ')}abstract NamedDomainObjectContainer<${nestedType.typeName}> get${capitalize(nestedType.name)}();"
                    lines << "public NamedDomainObjectContainer<? extends ${nestedType.typeName}> getOut${capitalize(nestedType.name)}() { return get${capitalize(nestedType.name)}(); };"
                } else {
                    lines << "${renderAnnotations(nestedType.allAnnotations, '                ')}public abstract NamedDomainObjectContainer<${nestedType.typeName}> get${capitalize(nestedType.name)}();"
                }
            } else if (nestedType.isUndiscoverable) {
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
                def sideEffect = showsConfigureInvocations && !nestedType.isSharedRef
                    ? "is${capitalize(nestedType.name)}Configured = true; // TODO: get rid of the side effect in the getter"
                    : ""
                lines << """
                ${renderAnnotations(nestedType.allAnnotations, '                ')}public ${nestedType.typeName} get${capitalize(nestedType.name)}() {
                    ${sideEffect}
                    return ${nestedType.name};
                }
                """
            } else {
                // Effectively-INTERFACE regular nested under an abstract-class outer:
                // no field, Gradle's ObjectFactory synthesizes the instance for the @Nested
                // abstract getter. Cannot carry side effects, enforced at build() time.
                lines << """${renderAnnotations(nestedType.allAnnotations, '                ')}@Nested
                public abstract ${nestedType.typeName} get${capitalize(nestedType.name)}();"""
            }
        }
        return lines.join("\n")
    }

    static String generateUndiscoverableInterface(PropertyTypeDeclaration nestedType) {
        def extendsClause = nestedType.implementsDefinition && nestedType.buildModel
            ? "extends ${Definition.class.simpleName}<${nestedType.typeName}.${nestedType.buildModel.className}>"
            : ""

        def services = generateInjectedServiceDeclarations(nestedType.injectedServices, false)

        def propertyGetters = nestedType.properties.collect { property ->
            "${renderAnnotations(property.allAnnotations, '                ')}public abstract ${getPropertyReturnType(property)} get${capitalize(property.name)}();"
        }.join("\n")

        // Undiscoverable is itself an interface; sub accessors use interface-member form.
        def nestedAccessors = nestedType.nestedTypes.collect { sub ->
            renderSubAccessor(sub, false)
        }.join("\n\n")

        def nestedInterfaces = renderNestedBodies(nestedType.nestedTypes, Shape.INTERFACE)

        def bmIface = ""
        if (nestedType.buildModel) {
            def buildModelPropertyGetters = nestedType.buildModel.properties.collect { property ->
                "${renderAnnotations(property.allAnnotations, '                    ')}${getPropertyReturnType(property)} get${capitalize(property.name)}();"
            }.join("\n")
            bmIface = """
                public interface ${nestedType.buildModel.className} extends BuildModel {
                    ${buildModelPropertyGetters}
                }
            """
        }

        return """
            public interface ${nestedType.typeName} ${extendsClause} {
                ${services}

                ${propertyGetters}

                ${nestedAccessors}

                ${nestedInterfaces}

                ${bmIface}
            }
        """
    }

    private String getDependenciesInterfaceContent() {
        if (!dependenciesDeclaration) {
            return ""
        }
        def collectorGetters = dependenciesDeclaration.collectors.collect { name ->
            "DependencyCollector get${capitalize(name)}();"
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

    static String generateAddingMethods(List<PropertyDeclaration> props) {
        def lines = []
        props.findAll { it.isList }.each { property ->
            lines << """
                @${Adding.class.simpleName}
                public void addTo${capitalize(property.name)}(${property.type.simpleName} value) {
                    get${capitalize(property.name)}().add(value);
                }
            """
        }
        return lines.join("\n")
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
            !nested.isNdoc && !nested.isUndiscoverable && !nested.isSharedRef &&
                effectiveShapeOf(nested, this.shape) == Shape.ABSTRACT_CLASS
        }.each { nestedType ->
            lines << """
                public String maybe${capitalize(nestedType.name)}Configured() {
                    return is${capitalize(nestedType.name)}Configured ? "(${nestedType.name} is configured)" : "";
                }
            """
        }
        return lines.join("\n")
    }

    private String generateImplConstructorAndFields() {
        def nestedFields = nestedTypes.findAll { !it.isNdoc }
        if (nestedFields.isEmpty()) {
            return ""
        }
        def fields = nestedFields.collect { "private final ${it.typeName} ${it.name};" }.join("\n")
        def inits = nestedFields.collect { "this.${it.name} = objects.newInstance(${it.typeName}.class);" }.join("\n")
        def getters = nestedFields.collect {
            """${renderAnnotations(it.allAnnotations, '                ')}@Override
                public ${it.typeName} get${capitalize(it.name)}() {
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

    static String generateInjectedServiceDeclarations(List<ServiceDeclaration> services, boolean isAbstract) {
        return services.collect { service ->
            if (isAbstract) {
                """@Inject
                protected abstract ${service.type.name} get${capitalize(service.name)}();"""
            } else {
                """@Inject
                ${service.type.name} get${capitalize(service.name)}();"""
            }
        }.join("\n")
    }

    private String generatePropertyMapping(PropertyDeclaration buildModelProperty, PropertyDeclaration definitionProperty, Language language) {
        def modelAccessor = propertyAccessor("model", buildModelProperty.name, language)
        def definitionAccessor = propertyAccessor("definition", definitionProperty.name, language)
        def statementEnd = (language == Language.KOTLIN) ? "" : ";"
        return "${modelAccessor}.set(${definitionAccessor})${statementEnd}"
    }

    private static String generatePropertyAccess(String objectExpression, PropertyDeclaration property, Language language) {
        def accessor = propertyAccessor(objectExpression, property.name, language)
        if (property.sharedTypeRef != null) {
            // Direct nested-object reference; no .get()/.getOrNull() unwrap.
            return accessor
        }
        if (property.isReadOnly || property.isJavaBean) {
            if (property.type == Directory || property.type == RegularFile) {
                def asFile = (language == Language.KOTLIN) ? ".asFile.absolutePath" : ".getAsFile().getAbsolutePath()"
                return "${accessor}${asFile}"
            }
            if (property.isReadOnly) {
                return accessor
            }
        }
        if (property.isList) {
            return "${accessor}.get()"
        }
        if (property.type == DirectoryProperty || property.type == RegularFileProperty) {
            def asFile = (language == Language.KOTLIN) ? ".asFile.absolutePath" : ".getAsFile().getAbsolutePath()"
            return "${accessor}.get()${asFile}"
        }
        if (property.type == Directory || property.type == RegularFile) {
            def asFile = (language == Language.KOTLIN) ? ".asFile.absolutePath" : ".getAsFile().getAbsolutePath()"
            return "${accessor}.get()${asFile}"
        }
        return "${accessor}.getOrNull()"
    }

    /**
     * Generates a property accessor expression appropriate for the language.
     * Java: {@code object.getFoo()}, Kotlin: {@code object.foo}.
     */
    static String propertyAccessor(String objectExpression, String propertyName, Language language) {
        if (language == Language.KOTLIN) {
            return "${objectExpression}.${propertyName}"
        }
        return "${objectExpression}.get${capitalize(propertyName)}()"
    }

    private static String printStatement(String objectType, String propertyName, String valueExpression, Language language) {
        return printCall("\"${objectType} ${propertyName} = \" + ${valueExpression}", language)
    }

    private static String printCall(String expression, Language language) {
        if (language == Language.KOTLIN) {
            return "println(${expression})"
        }
        return "System.out.println(${expression});"
    }

    static String getPropertyReturnType(PropertyDeclaration property) {
        if (property.sharedTypeRef != null) {
            return property.sharedTypeRef.typeName
        }
        if (property.isReadOnly) {
            return property.type.simpleName
        }
        if (property.isJavaBean) {
            return property.type.simpleName
        }
        if (property.isList) {
            return "ListProperty<${property.type.simpleName}>"
        }
        if (property.type == DirectoryProperty) {
            return "DirectoryProperty"
        }
        if (property.type == RegularFileProperty) {
            return "RegularFileProperty"
        }
        return "Property<${property.type.simpleName}>"
    }

    private static boolean isFileType(Class type) {
        return type in [DirectoryProperty, RegularFileProperty, Directory, RegularFile]
    }

    static String propertyFieldInitializer(PropertyDeclaration property) {
        if (property.type == Directory) {
            return "objects.directoryProperty()"
        }
        if (property.type == RegularFile) {
            return "objects.fileProperty()"
        }
        throw new IllegalStateException("Unsupported property field type: ${property.type}")
    }

    static String capitalize(String name) {
        return name.length() == 1 ? name.toUpperCase() : name[0].toUpperCase() + name[1..-1]
    }

    /**
     * Renders user-supplied annotation source fragments as a prefix to be inserted immediately
     * before a getter. For an empty list, returns the empty string so existing output is
     * byte-identical. For a non-empty list, returns each annotation joined by a newline + the
     * supplied indent, with a trailing newline + indent so the caller's next token (the getter
     * keyword) sits on a fresh, correctly-indented line.
     */
    static String renderAnnotations(List<String> annotations, String indent) {
        if (annotations.isEmpty()) {
            return ""
        }
        return annotations.collect { "${it}\n${indent}" }.join("")
    }
}
