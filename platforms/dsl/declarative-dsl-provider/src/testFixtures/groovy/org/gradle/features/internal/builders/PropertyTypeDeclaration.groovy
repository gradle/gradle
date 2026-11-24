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
 * <p>The {@link #kind} discriminator selects between four mutually exclusive forms,
 * each carrying its own {@link #kindData} holder:</p>
 * <ul>
 *     <li>{@link NestedKind#PLAIN} — a regular {@code @Nested} type.</li>
 *     <li>{@link NestedKind#NDOC} — a {@code NamedDomainObjectContainer} element type.</li>
 *     <li>{@link NestedKind#UNDISCOVERABLE} — a private-field-owned nested object with no
 *         public getter, accessed only via the generated {@code foo(Action)} method.</li>
 *     <li>{@link NestedKind#SHARED_REF} — either a top-level shared-type declaration (owned
 *         by {@code TestScenarioBuilder.sharedTypes}) or a use-site reference to one.</li>
 * </ul>
 *
 * <p>Construct via the static factory methods so {@code kind} and {@code kindData} stay in sync.</p>
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
     * Discriminator for which "kind" this declaration is. Always paired with a matching
     * {@link #kindData} holder; construct via the static factory methods to keep them in sync.
     */
    NestedKind kind = NestedKind.PLAIN

    /**
     * Per-kind data holder. Type depends on {@link #kind}:
     * {@link PlainKindData}, {@link NdocKindData}, {@link UndiscoverableKindData},
     * or {@link SharedRefKindData}.
     */
    Object kindData = new PlainKindData()

    // --- Factory methods ---

    /** Creates a plain {@code @Nested} declaration. */
    static PropertyTypeDeclaration plain(String name, String typeName) {
        return new PropertyTypeDeclaration(
            name: name, typeName: typeName,
            kind: NestedKind.PLAIN, kindData: new PlainKindData()
        )
    }

    /**
     * Creates a {@code NamedDomainObjectContainer<T>} declaration.
     *
     * <p>Named {@code ndocOf} (rather than {@code ndoc}) to avoid clashing with the
     * instance {@code ndoc(name, typeName, Closure)} method contributed by the
     * {@link org.gradle.features.internal.builders.dsl.HasNestedTypes} trait.</p>
     */
    static PropertyTypeDeclaration ndocOf(String name, String typeName) {
        return new PropertyTypeDeclaration(
            name: name, typeName: typeName,
            kind: NestedKind.NDOC, kindData: new NdocKindData()
        )
    }

    /** Creates an undiscoverable nested-type declaration. */
    static PropertyTypeDeclaration undiscoverable(String name, String typeName) {
        return new PropertyTypeDeclaration(
            name: name, typeName: typeName,
            kind: NestedKind.UNDISCOVERABLE, kindData: new UndiscoverableKindData()
        )
    }

    /**
     * Creates a use-site reference to a previously-declared shared type.
     * Copies the declaration metadata and reuses the referenced declaration's shared shape.
     */
    static PropertyTypeDeclaration sharedRef(String name, PropertyTypeDeclaration ref) {
        def refShape = ref.kind == NestedKind.SHARED_REF
            ? ((SharedRefKindData) ref.kindData).sharedShape
            : TypeShape.INTERFACE
        return new PropertyTypeDeclaration(
            name: name,
            typeName: ref.typeName,
            properties: ref.properties,
            nestedTypes: ref.nestedTypes,
            injectedServices: ref.injectedServices,
            implementsDefinition: ref.implementsDefinition,
            buildModel: ref.buildModel,
            kind: NestedKind.SHARED_REF,
            kindData: new SharedRefKindData(sharedShape: refShape)
        )
    }

    /**
     * Creates the top-level declaration for a {@code TestScenarioBuilder.sharedType(...)}.
     * The declaration carries {@link NestedKind#SHARED_REF} so {@link SharedTypeBuilder}
     * can read {@code sharedShape} from it; the use-site copy created by {@link #sharedRef}
     * uses the same kind.
     */
    static PropertyTypeDeclaration sharedDeclaration(String typeName) {
        return new PropertyTypeDeclaration(
            typeName: typeName,
            kind: NestedKind.SHARED_REF,
            kindData: new SharedRefKindData()
        )
    }

    // --- Typed accessors ---

    /** Returns the kind data as {@link PlainKindData} or throws if {@link #kind} is not PLAIN. */
    PlainKindData plainData() {
        if (kind != NestedKind.PLAIN) {
            throw new IllegalStateException("Not a PLAIN nested type: kind=${kind}")
        }
        return (PlainKindData) kindData
    }

    /** Returns the kind data as {@link NdocKindData} or throws if {@link #kind} is not NDOC. */
    NdocKindData ndocData() {
        if (kind != NestedKind.NDOC) {
            throw new IllegalStateException("Not an NDOC nested type: kind=${kind}")
        }
        return (NdocKindData) kindData
    }

    /** Returns the kind data as {@link UndiscoverableKindData} or throws if {@link #kind} is not UNDISCOVERABLE. */
    UndiscoverableKindData undiscoverableData() {
        if (kind != NestedKind.UNDISCOVERABLE) {
            throw new IllegalStateException("Not an UNDISCOVERABLE nested type: kind=${kind}")
        }
        return (UndiscoverableKindData) kindData
    }

    /** Returns the kind data as {@link SharedRefKindData} or throws if {@link #kind} is not SHARED_REF. */
    SharedRefKindData sharedRefData() {
        if (kind != NestedKind.SHARED_REF) {
            throw new IllegalStateException("Not a SHARED_REF nested type: kind=${kind}")
        }
        return (SharedRefKindData) kindData
    }

    // --- DSL methods ---

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

    /**
     * Rejects asNdoc() on declarations not constructed via {@link #ndoc}. Retained as a DSL
     * setter so {@code sharedType { asNdoc() }} continues to throw an {@code IllegalStateException}
     * with "asNdoc" in the message. The {@code ndoc(name, type)} DSL is the only legitimate path.
     */
    void asNdoc() {
        throw new IllegalStateException(
            "asNdoc() is not allowed on this declaration; NDOC-ness is fixed at construction. " +
            "Use ndoc(name, typeName) at the containment site instead."
        )
    }

    /** Marks the NDOC getter as out-projected ({@code NamedDomainObjectContainer<? extends T>}). */
    void outProjected() {
        if (kind != NestedKind.NDOC) {
            throw new IllegalStateException(
                "outProjected() is only valid on NDOC nested types (kind=${kind})."
            )
        }
        ((NdocKindData) kindData).outProjected = true
    }

    /**
     * Supplies Java code to be inlined into the enclosing definition's constructor, right after
     * the undiscoverable field is created. The code may reference the field by its unqualified
     * name (e.g. {@code "foo.getBar().set(\"default\");"}). Only valid for undiscoverable
     * nested types.
     */
    void initializeWith(String code) {
        if (kind != NestedKind.UNDISCOVERABLE) {
            throw new IllegalStateException(
                "initializeWith(...) is only valid on undiscoverable nested types (kind=${kind})."
            )
        }
        ((UndiscoverableKindData) kindData).initializationCode = code
    }

    /**
     * Sets the shape of the generated nested type body. Overrides the shape inherited
     * from the enclosing definition (or enclosing nested). Only valid for
     * {@link NestedKind#PLAIN} declarations: NDOC, undiscoverable, and shared-ref forms
     * have a fixed body shape.
     */
    void shape(TypeShape s) {
        if (kind != NestedKind.PLAIN) {
            throw new IllegalStateException(
                "shape(...) is not supported on NDOC, undiscoverable, or shared-ref nested types; " +
                "their body shape is fixed."
            )
        }
        ((PlainKindData) kindData).shape = s
    }
}
