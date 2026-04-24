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
 * DSL delegate used inside {@code TestScenarioBuilder.sharedType(typeName) { ... }} blocks.
 *
 * <p>Forwards allowed configuration to the underlying {@link PropertyTypeDeclaration} and
 * rejects methods that only make sense when a type is contained within another type —
 * {@link #asNdoc()}, {@link #outProjected()}, {@link #undiscoverable}, and
 * {@link #initializeWith(String)} all throw {@link IllegalStateException}.</p>
 *
 * <p>{@link #implementsDefinition(String, Closure)} is allowed structurally. However,
 * auto-mapping on a referring definition/build model is skipped for such shared types
 * because they are not registered via feature binding; tests that need propagation must
 * supply an explicit {@code mapping(...)} block on the referring build model.</p>
 */
class SharedTypeDsl {
    private final PropertyTypeDeclaration target

    SharedTypeDsl(PropertyTypeDeclaration target) { this.target = target }

    // --- Allowed forwards ---

    /** Adds a simple property. */
    void property(String name, Class type) { target.property(name, type) }

    /** Adds a simple property with optional configuration (e.g. annotations). */
    void property(String name, Class type,
        @DelegatesTo(value = PropertyDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config
    ) { target.property(name, type, config) }

    /** Adds a {@code ListProperty<T>} property. */
    void listProperty(String name, Class elementType) { target.listProperty(name, elementType) }

    /** Adds a {@code ListProperty<T>} property with optional configuration. */
    void listProperty(String name, Class elementType,
        @DelegatesTo(value = PropertyDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config
    ) { target.listProperty(name, elementType, config) }

    /** Adds a sub-nested type with its own properties. */
    void property(String name, String nestedTypeName,
        @DelegatesTo(value = PropertyTypeDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config = {}
    ) { target.property(name, nestedTypeName, config) }

    /** Adds a {@code NamedDomainObjectContainer} sub-nested type. */
    void ndoc(String name, String elementTypeName,
        @DelegatesTo(value = PropertyTypeDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config = {}
    ) { target.ndoc(name, elementTypeName, config) }

    /** Adds an injected service. */
    void injectedService(String name, Class type) { target.injectedService(name, type) }

    /** Adds annotation source fragments to be emitted on the getter at each use-site. */
    void annotations(String... items) { target.annotations(items) }

    /**
     * Declares that this shared type extends {@code Definition<BuildModel>}.
     *
     * <p>This is allowed structurally, but any referring definition/build model will
     * <strong>not</strong> auto-map this shared-ref property because shared types are
     * not registered via feature binding and {@code context.getBuildModel(sharedInstance)}
     * will not resolve. Supply a custom {@code mapping(...)} block if propagation is needed.</p>
     */
    void implementsDefinition(String buildModelName,
        @DelegatesTo(value = BuildModelDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config = {}
    ) { target.implementsDefinition(buildModelName, config) }

    /** Sets the top-level class shape for this shared type. Default: {@link TypeShape#INTERFACE}. */
    void shape(TypeShape s) { target.sharedShape = s }

    // --- Disallowed ---

    /**
     * Rejected: NDOC-ness is a property of the use-site containment, not of the shared type itself.
     */
    void asNdoc() {
        throw new IllegalStateException("asNdoc() is not allowed on a sharedType; NDOC-ness is a property of the use-site containment.")
    }

    /** Rejected: see {@link #asNdoc()}. */
    void outProjected() {
        throw new IllegalStateException("outProjected() is not allowed on a sharedType.")
    }

    /** Rejected: undiscoverability is a containment-site concern, not a type-declaration concern. */
    void undiscoverable(String name, String typeName,
        @DelegatesTo(value = PropertyTypeDeclaration, strategy = Closure.DELEGATE_FIRST)
        Closure config = {}
    ) {
        throw new IllegalStateException("undiscoverable(...) is not allowed on a sharedType; it is a containment-site concern.")
    }

    /** Rejected: only meaningful for undiscoverable fields, which are not allowed on shared types. */
    void initializeWith(String code) {
        throw new IllegalStateException("initializeWith(...) is not allowed on a sharedType; it is only meaningful for undiscoverable fields.")
    }
}
