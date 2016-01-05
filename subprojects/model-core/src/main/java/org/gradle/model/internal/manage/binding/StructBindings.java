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
 *     <li>abstract methods that are present in the delegate type are delegated to an instance of the delegate type (see {@link DelegateMethodBinding})</li>
 *     <li>abstract property-accessor methods are implemented by a generated property (the value of the property stored in a child-node, see {@link ManagedPropertyMethodBinding})</li>
 * </ul>
 *
 * @see org.gradle.model.internal.manage.schema.extract.ManagedProxyClassGenerator
 */
public interface StructBindings<T> {
    /**
     * Returns the schema for the public view.
     */
    StructSchema<T> getPublicSchema();

    /**
     * Returns schemas for the internal views.
     */
    Iterable<StructSchema<?>> getInternalViewSchemas();

    /**
     * Returns schemas for both the public and internal views.
     */
    Set<StructSchema<?>> getAllViewSchemas();

    /**
     * Returns the delegate schema, or {@code null} if a delegate schema is not defined.
     */
    @Nullable
    StructSchema<?> getDelegateSchema();

    /**
     * Returns the managed properties declared by the struct.
     */
    Map<String, ManagedProperty<?>> getManagedProperties();

    /**
     * Returns the methods that are implemented by the public view.
     */
    Collection<DirectMethodBinding> getViewBindings();

    /**
     * Returns the methods that are implemented by the delegate.
     */
    Collection<DelegateMethodBinding> getDelegateBindings();
}
