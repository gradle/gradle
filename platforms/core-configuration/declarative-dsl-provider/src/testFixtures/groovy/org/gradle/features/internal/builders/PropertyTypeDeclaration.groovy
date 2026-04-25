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
import org.gradle.features.internal.builders.dsl.HasAnnotations
import org.gradle.features.internal.builders.dsl.HasInjectedServices
import org.gradle.features.internal.builders.dsl.HasNestedTypes
import org.gradle.features.internal.builders.dsl.HasProperties

/**
 * Describes a nested type within a definition.
 *
 * <p>A nested type can be:</p>
 * <ul>
 *     <li>A plain {@code @Nested} type: generates a getter annotated with {@code @Nested}.</li>
 *     <li>A {@code NamedDomainObjectContainer} (NDOC) element type.</li>
 *     <li>An <em>undiscoverable</em> type: owned by the enclosing abstract-class definition as a
 *         {@code private final} field, initialized in the constructor via {@code ObjectFactory},
 *         with no {@code @Nested} annotation and no public getter. The only entry point is the
 *         generated {@code foo(Action)} method, which configures the instance stored in the field.
 *         Optional user-supplied Java code can be inlined into the enclosing constructor via
 *         {@link #initialize(String)} to initialize state on the newly-created instance.</li>
 * </ul>
 *
 * <p>Nested types can themselves contain properties, injected services, sub-nested types, and
 * optionally implement {@code Definition<BuildModel>} (for NDOC elements that are definitions).</p>
 */
class PropertyTypeDeclaration implements HasProperties, HasNestedTypes, HasInjectedServices, HasAnnotations {
    /** The accessor name for this nested type (e.g. "foo" generates "getFoo()"). */
    String name

    /** The type name of the nested type (e.g. "Foo"). */
    String typeName

    /** Whether this nested type extends {@code Definition<BuildModel>}. */
    boolean implementsDefinition = false

    /** The build model for this nested type, if it implements Definition. */
    BuildModelDeclaration buildModel = null

    /** Whether this nested type is a {@code NamedDomainObjectContainer} element. */
    boolean isNdoc = false

    /** Whether the NDOC getter uses out-projection ({@code ? extends T}). */
    boolean isOutProjected = false

    /**
     * Whether this nested type is undiscoverable: owned by the enclosing definition as a
     * {@code private final} field, initialized in the constructor via {@code ObjectFactory},
     * with no {@code @Nested} annotation and no public getter. Requires the enclosing definition
     * to have {@code ABSTRACT_CLASS} shape. Mutually exclusive with {@link #isNdoc}.
     */
    boolean isUndiscoverable = false

    /**
     * User-supplied Java code inlined into the enclosing definition's constructor, immediately
     * after the undiscoverable field is initialized via {@code objects.newInstance(...)}. The
     * code may reference the field by its unqualified name (e.g. {@code foo.getBar().set(...);}).
     * Only consulted for undiscoverable nested types.
     */
    String initializationCode = null

    /** Simple properties on this nested type. */
    List<PropertyDeclaration> properties = []

    /** Injected services on this nested type. */
    List<ServiceDeclaration> injectedServices = []

    /** Sub-nested types within this nested type. */
    List<PropertyTypeDeclaration> nestedTypes = []

    /**
     * Annotation source fragments (e.g. "@Incubating") to emit on the getter for this nested
     * type. Each entry is rendered verbatim on its own line immediately before the getter.
     * Not supported on an undiscoverable top-level declaration (no public getter exists);
     * attempting to set these via {@code undiscoverable(...)} throws at DSL build time.
     */
    List<String> allAnnotations = []

    /**
     * When true, this declaration is a USE-SITE reference to a previously declared shared type
     * (see {@code TestScenarioBuilder.sharedType(...)}). Renderers that walk the enclosing
     * definition's nested types emit the getter, field, constructor init, and mapping as usual
     * but SKIP generating an inner-type body for this entry — the type lives in its own
     * top-level file produced by {@link SharedTypeBuilder}.
     */
    boolean isSharedRef = false

    /**
     * For top-level rendering via {@link SharedTypeBuilder}: the class shape to emit
     * (interface or abstract class). Ignored when this declaration is used as an inner
     * nested type.
     */
    TypeShape sharedShape = TypeShape.INTERFACE

    /**
     * Explicit shape for this nested type's emitted body. When null, the effective
     * shape is inherited from the enclosing definition (for top-level nested types)
     * or from the enclosing nested's effective shape (for sub-nested types).
     *
     * <p>Ignored for {@code isNdoc}, {@code isUndiscoverable}, and {@code isSharedRef}:
     * those emit a fixed form dictated by their kind. Attempting to set a shape on
     * such a declaration via {@link #shape(TypeShape)} throws.</p>
     */
    TypeShape shape = null

    /**
     * Adds a sub-nested type with its own properties.
     *
     * @deprecated Use {@link HasNestedTypes#nested(String, String, Closure)} instead.
     */
    @Deprecated
    void property(String name, String nestedTypeName,
        @DelegatesTo(value = PropertyTypeDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config = {}
    ) {
        nested(name, nestedTypeName, config)
    }

    /**
     * Marks this nested type as implementing {@code Definition<BuildModel>}, which
     * makes it eligible for NDOC-based registration and feature binding.
     *
     * @param buildModelName the class name of the build model interface
     * @param config optional configuration for the build model's properties
     */
    void implementsDefinition(String buildModelName,
        @DelegatesTo(value = BuildModelDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config = {}
    ) {
        this.implementsDefinition = true
        this.buildModel = new BuildModelDeclaration(className: buildModelName)
        ClosureConfigure.configure(this.buildModel, config)
    }

    /** Marks this type as a {@code NamedDomainObjectContainer} element. */
    void asNdoc() { this.isNdoc = true }

    /** Marks the NDOC getter as out-projected ({@code NamedDomainObjectContainer<? extends T>}). */
    void outProjected() { this.isOutProjected = true }

    /**
     * Supplies Java code to be inlined into the enclosing definition's constructor, right after
     * the undiscoverable field is created. The code may reference the field by its unqualified
     * name (e.g. {@code "foo.getBar().set(\"default\");"}). Only meaningful for undiscoverable
     * nested types; ignored otherwise.
     */
    void initializeWith(String code) { this.initializationCode = code }

    /**
     * Sets the shape of the generated nested type body. Overrides the shape inherited
     * from the enclosing definition (or enclosing nested). Not supported on NDOC,
     * undiscoverable, or shared-ref declarations: those always emit a fixed form.
     */
    void shape(TypeShape s) {
        if (isNdoc || isUndiscoverable || isSharedRef) {
            throw new IllegalStateException(
                "shape(...) is not supported on NDOC, undiscoverable, or shared-ref nested types; " +
                "their body shape is fixed."
            )
        }
        this.shape = s
    }
}
