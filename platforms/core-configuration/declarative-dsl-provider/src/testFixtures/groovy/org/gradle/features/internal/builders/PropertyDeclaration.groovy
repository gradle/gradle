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

/**
 * Describes a property to be generated in a definition or build model type.
 *
 * <p>The {@link #kind} discriminator selects between five mutually exclusive forms:</p>
 * <ul>
 *     <li>{@link PropertyKind#PROPERTY} — a {@code Property<T>} getter (default).</li>
 *     <li>{@link PropertyKind#LIST_PROPERTY} — a {@code ListProperty<T>} getter.</li>
 *     <li>{@link PropertyKind#READ_ONLY} — a concrete {@code T} getter (e.g. {@code Directory getDir()}).</li>
 *     <li>{@link PropertyKind#JAVA_BEAN} — a getter/setter pair. Carries {@link JavaBeanKindData}.</li>
 *     <li>{@link PropertyKind#SHARED_REF} — a use-site reference to a shared type.
 *         Carries {@link SharedRefPropertyData}.</li>
 * </ul>
 *
 * <p>Some types receive special handling regardless of kind: {@code DirectoryProperty}
 * and {@code RegularFileProperty} render their own type name directly rather than
 * wrapping in {@code Property<T>}.</p>
 *
 * <p>Construct via the static factory methods so {@code kind} and {@code kindData} stay in sync.</p>
 */
class PropertyDeclaration {
    /** The property name, used to derive the getter name (e.g. "text" becomes "getText()"). */
    String name

    /** The property type (e.g. {@code String}, {@code DirectoryProperty}, {@code Directory}). */
    Class type

    /** Annotation source fragments (e.g. "@Incubating") to emit on the getter. Each entry is rendered verbatim on its own line immediately before the getter. */
    List<String> allAnnotations = []

    /**
     * Discriminator for which "kind" this property is. Always paired with a matching
     * {@link #kindData} holder; construct via the static factory methods to keep them in sync.
     */
    PropertyKind kind = PropertyKind.PROPERTY

    /**
     * Per-kind data holder. {@code null} for PROPERTY, LIST_PROPERTY, READ_ONLY.
     * {@link JavaBeanKindData} for JAVA_BEAN. {@link SharedRefPropertyData} for SHARED_REF.
     */
    Object kindData = null

    // --- Factory methods ---

    /** Creates a {@code Property<T>} property declaration. */
    static PropertyDeclaration property(String name, Class type) {
        return new PropertyDeclaration(name: name, type: type, kind: PropertyKind.PROPERTY)
    }

    /** Creates a {@code ListProperty<T>} property declaration. */
    static PropertyDeclaration listProperty(String name, Class elementType) {
        return new PropertyDeclaration(name: name, type: elementType, kind: PropertyKind.LIST_PROPERTY)
    }

    /** Creates a read-only (concrete-type) property declaration. */
    static PropertyDeclaration readOnly(String name, Class type) {
        return new PropertyDeclaration(name: name, type: type, kind: PropertyKind.READ_ONLY)
    }

    /** Creates a Java-bean-style property declaration with a default {@code ABSTRACT} style. */
    static PropertyDeclaration javaBean(String name, Class type) {
        return new PropertyDeclaration(
            name: name, type: type,
            kind: PropertyKind.JAVA_BEAN, kindData: new JavaBeanKindData()
        )
    }

    /** Creates a shared-ref property declaration referencing a previously declared shared type. */
    static PropertyDeclaration sharedRef(String name, PropertyTypeDeclaration ref) {
        return new PropertyDeclaration(
            name: name,
            kind: PropertyKind.SHARED_REF, kindData: new SharedRefPropertyData(ref: ref)
        )
    }

    // --- Typed accessors ---

    /** Returns the kind data as {@link JavaBeanKindData} or throws if {@link #kind} is not JAVA_BEAN. */
    JavaBeanKindData javaBeanData() {
        if (kind != PropertyKind.JAVA_BEAN) {
            throw new IllegalStateException("Not a JAVA_BEAN property: kind=${kind}")
        }
        return (JavaBeanKindData) kindData
    }

    /** Returns the kind data as {@link SharedRefPropertyData} or throws if {@link #kind} is not SHARED_REF. */
    SharedRefPropertyData sharedRefData() {
        if (kind != PropertyKind.SHARED_REF) {
            throw new IllegalStateException("Not a SHARED_REF property: kind=${kind}")
        }
        return (SharedRefPropertyData) kindData
    }

    // --- DSL methods ---

    /** Sets the style of this Java-bean property declaration. Only valid for {@link PropertyKind#JAVA_BEAN}. */
    void shape(JavaBeanStyle s) {
        if (kind != PropertyKind.JAVA_BEAN) {
            throw new IllegalStateException(
                "shape(...) is only supported on Java-bean properties (kind=${kind})."
            )
        }
        ((JavaBeanKindData) kindData).style = s
    }

    /** Adds annotation source fragments to be emitted on the getter. Each string is inserted verbatim (including the leading {@code @}). */
    void annotations(String... items) { this.allAnnotations.addAll(items as List) }
}
