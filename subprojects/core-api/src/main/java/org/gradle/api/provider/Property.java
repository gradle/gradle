/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.provider;

import org.gradle.api.SupportsKotlinAssignmentOverloading;
import org.gradle.api.model.ObjectFactory;

import javax.annotation.Nullable;

/**
 * A container object that represents a configurable value of a specific type. A {@link Property} is also a
 * {@link Provider} and can be used in the same way as a {@link Provider}. A property's value can be accessed
 * using the methods of {@link Provider} such as {@link Provider#get() get()}. The value can be modified by
 * using the methods {@link #set(Object)} and {@link #set(Provider)}, or their fluid API counterparts
 * {@link #value(Object)} and {@link #value(Provider)}.
 *
 * <p>
 * A property may represent a task output. Such a property carries information about the task producing
 * its value. When this property is attached to an input of another task, Gradle will automatically determine
 * the task dependencies based on this connection.
 * </p>
 *
 * <p>
 * You can create a {@link Property} instance using {@link ObjectFactory#property(Class)}. There are
 * also several specialized subtypes of this interface that can be created using various other factory methods.
 * </p>
 *
 * <p>
 * <b>Note:</b> This interface is not intended for implementation by build script or plugin authors.
 * </p>
 *
 * @param <T> Type of value represented by the property
 * @since 4.3
 */
@SupportsKotlinAssignmentOverloading
public interface Property<T> extends Provider<T>, HasConfigurableValue {
    /**
     * Sets the value of the property to the given value, replacing whatever value the property already had.
     *
     * <p>
     * This method can also be used to discard the value of the property, by passing it {@code null}. When the
     * value is discarded (or has never been set in the first place), the convention (default value) for this
     * property, if specified, will be used to provide the value instead.
     * </p>
     *
     * @param value The value, can be null.
     */
    void set(@Nullable T value);

    /**
     * Sets the property to have the same value as the given provider, replacing whatever value the property already had.
     * This property will track the value of the provider and query its value each time the value of the property is queried.
     * When the provider has no value, this property will also have no value.
     *
     * <p>
     * This method can NOT be used to discard the value of the property. Specifying a {@code null} provider will result
     * in an {@code IllegalArgumentException} being thrown. When the provider has no value, this property will also have
     * no value - regardless of whether or not a convention has been set.
     * </p>
     *
     * <p>
     * When the given provider represents a task output, this property will also carry the task dependency information
     * from the provider.
     * </p>
     *
     * @param provider The provider of the property's value, can't be null.
     */
    void set(Provider<? extends T> provider);

    /**
     * Sets the value of the property to the given value, replacing whatever value the property already had.
     * This is the same as {@link #set(Object)} but returns this property to allow method chaining.
     *
     * <p>
     * This method can also be used to discard the value of the property, by passing it {@code null}.
     * When the value is discarded (or has never been set in the first place), the convention (default value)
     * for this property, if specified, will be used to provide the value instead.
     * </p>
     *
     * @param value The value, can be null.
     * @return this
     * @since 5.0
     */
    Property<T> value(@Nullable T value);

    /**
     * Sets the property to have the same value as the given provider, replacing whatever value the property already had.
     * This property will track the value of the provider and query its value each time the value of the property is queried.
     * When the provider has no value, this property will also have no value. This is the same as {@link #set(Provider)}
     * but returns this property to allow method chaining.
     *
     * <p>
     * This method can NOT be used to discard the value of the property. Specifying a {@code null} provider will result
     * in an {@code IllegalArgumentException} being thrown. When the provider has no value, this property will also have
     * no value - regardless of whether or not a convention has been set.
     * </p>
     *
     * <p>
     * When the given provider represents a task output, this property will also carry the task dependency information
     * from the provider.
     * </p>
     *
     * @param provider The provider whose value to use.
     * @return this
     * @since 5.6
     */
    Property<T> value(Provider<? extends T> provider);

    /**
     * Specifies the value to use as the convention (default value) for this property. If the convention is set and
     * no explicit value or value provider has been specified, then the convention will be returned as the value of
     * the property (when queried).
     *
     * <p>
     * This method can be used to specify that the property does not have a default value, by passing it
     * {@code null}.
     * </p>
     *
     * @param value The convention value, or {@code null} if the property should have no default value.
     * @return this
     * @since 5.1
     */
    Property<T> convention(@Nullable T value);

    /**
     * Specifies the provider to be used to query the convention (default value) for this property. If a convention
     * provider has been set and no explicit value or value provider has been specified, then the convention
     * provider's value will be returned as the value of the property (when queried).
     *
     * <p>
     * The property's convention tracks the convention provider. Whenever the convention's actual value is
     * needed, the convention provider will be queried anew.
     * </p>
     *
     * <p>
     * This method can't be used to specify that a property does not have a default value. Passing in a {@code null}
     * provider will result in an {@code IllegalArgumentException} being thrown. When the provider doesn't have
     * a value, then the property will behave as if it wouldn't have a convention specified.
     * </p>
     *
     * @param provider The provider of the property's convention value, can't be null.
     * @return this
     * @since 5.1
     */
    Property<T> convention(Provider<? extends T> provider);

    /**
     * Disallows further changes to the value of this property. Calls to methods that change the value of this property,
     * such as {@link #set(Object)}, {@link #set(Provider)}, {@link #value(Object)} and {@link #value(Provider)} will fail,
     * by throwing an {@code IllegalStateException}.
     *
     * <p>
     * When this property's value is specified via a {@link Provider}, calling {@code finalizeValue()} will trigger the
     * querying of the provider and the obtained value will be set as the final value of the property. The value of the
     * provider will not be tracked further.
     * </p>
     *
     * <p>
     * Note that although the value of the property will not change, the value itself may be a mutable object. Calling
     * this method does not guarantee that the value will become immutable.
     * </p>
     *
     * @since 5.0
     */
    @Override
    void finalizeValue();
}
