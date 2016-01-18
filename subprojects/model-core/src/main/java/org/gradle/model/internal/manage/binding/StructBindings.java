/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.model.internal.manage.binding;

import org.gradle.api.Nullable;
import org.gradle.model.internal.manage.schema.StructSchema;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * A type representing the bindings of a struct type's view methods to their implementations.
 *
 * <p>
 * A struct type is declared by the following elements:
 * </p>
 *
 * <ul>
 *     <li>a public type (can be fully or partially abstract)</li>
 *     <li>zero or more internal views (declared as interfaces)</li>
 *     <li>an optional delegate type (must be a concrete type)</li>
 * </ul>
 *
 * <p>
 * When a struct type node is projected as one of its views, each method in the view must be implemented. These bindings
 * contain the information about how each view method should be implemented by the view proxy.
 * There are three ways a method can be implemented like:
 * </p>
 *
 * <ul>
 *     <li>non-abstract methods on the public view are implemented by the view itself (see {@link DirectMethodBinding})</li>
 *     <li>abstract methods that are present in the public type are bridged to the public implementation (see {@link BridgeMethodBinding})</li>
 *     <li>abstract methods that are present in the delegate type are delegated to an instance of the delegate type (see {@link DelegateMethodBinding})</li>
 *     <li>abstract property-accessor methods are implemented by a generated property (the value of the property stored in a child-node, see {@link ManagedPropertyMethodBinding})</li>
 * </ul>
 *
 * @see ManagedProperty
 * @see org.gradle.model.internal.manage.schema.extract.ManagedProxyClassGenerator
 */
public interface StructBindings<T> {
    /**
     * Returns the schema for the public view.
     */
    StructSchema<T> getPublicSchema();

    /**
     * Returns the view schemas used in declaring the struct. These schemas include the public view schema and any internal view schemas specified
     * when declaring the struct.
     */
    Set<StructSchema<?>> getDeclaredViewSchemas();

    /**
     * Returns all schemas that are implemented by this struct. These schemas include the public view schema, any internal view schemas specified
     * when declaring the struct, together with all their super-type schemas. This is an exhaustive list of every type a node can be
     * viewed as. In other words this is the full list of interfaces that a view proxy class generated for this struct type might implement.
     *
     * <p>Note: currently view proxies also implement any interfaces implemented by the delegate type. The set returned by this method
     * includes schemas for all those interfaces as well, together with schemas for their super-interfaces.</p>
     */
    Set<StructSchema<?>> getImplementedViewSchemas();

    /**
     * Returns the delegate schema, or {@code null} if a delegate schema is not defined.
     */
    @Nullable
    StructSchema<?> getDelegateSchema();

    /**
     * Returns the managed properties inferred from the view and delegate schemas declaring this struct.
     *
     * @see ManagedProperty
     */
    Map<String, ManagedProperty<?>> getManagedProperties();

    /**
     * Returns the managed property with the given name, or {@code null} if such a property is not found.
     */
    ManagedProperty<?> getManagedProperty(String name);

    /**
     * Returns the method bindings required to implement the struct.
     */
    Collection<StructMethodBinding> getMethodBindings();
}
