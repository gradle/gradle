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

import org.gradle.api.Incubating;

import javax.annotation.Nullable;

/**
 * A container object that represents a configurable value of a specific type. A {@link Property} is also a {@link Provider} and can be used in the same way as a {@link Provider}. A property's value can be accessed using the methods of {@link Provider} such as {@link Provider#get()}. The value can be modified by using the method {@link #set(Object)} or {@link #set(Provider)}.
 *
 * <p>
 * A property may be used to represent a task output. Such a property carries information about which task produces its value. When the property is attached to a task input, this allows Gradle to automatically calculate the dependencies between tasks based on the values they use as inputs and produce as outputs.
 * </p>
 *
 * <p>You can create a {@link Property} instance using {@link org.gradle.api.model.ObjectFactory#property(Class)}. There are also several specialized subtypes of this interface that can be created using various other factory methods.</p>
 *
 * <p><b>Note:</b> This interface is not intended for implementation by build script or plugin authors.</p>
 *
 * @param <T> Type of value represented by the property
 * @since 4.3
 */
@Incubating
public interface Property<T> extends Provider<T> {
    /**
     * Sets the value of the property the given value, replacing whatever value the property already had.
     *
     * <p>This method can also be used to clear the value of the property, by passing {@code null} as the value.
     *
     * @param value The value, can be null.
     */
    void set(@Nullable T value);

    /**
     * Sets the property to have the same value of the given provider, replacing whatever value the property already had. This property will track the value of the provider and query its value each time the value of the property is queried. When the provider has no value, this property will also have no value.
     *
     * <p>
     * When the given provider represents a task output, this property will also carry the task dependency information from the provider.
     * </p>
     *
     * @param provider Provider
     */
    void set(Provider<? extends T> provider);

    /**
     * Sets the value of the property the given value, replacing whatever value the property already had.
     *
     * <p>This is the same as {@link #set(Object)} but returns this property to allow method chaining.</p>
     *
     * @param value The value, can be null.
     * @return this
     * @since 5.0
     */
    Property<T> value(@Nullable T value);

    /**
     * Specifies the value to use as the convention for this property. The convention is used when no value has been set for this property.
     *
     * @param value The value.
     * @return this
     * @since 5.1
     */
    Property<T> convention(T value);

    /**
     * Specifies the provider of the value to use as the convention for this property. The convention is used when no value has been set for this property.
     *
     * @param valueProvider The provider of the value.
     * @return this
     * @since 5.1
     */
    Property<T> convention(Provider<? extends T> valueProvider);

    /**
     * Disallows further changes to the value of this property. Calls to methods that change the value of this property, such as {@link #set(Object)} or {@link #set(Provider)} will fail.
     *
     * <p>When this property has a value provided by a {@link Provider}, the value of the provider is queried when this method is called and the value of this property set to the result. The value of the provider will no longer be tracked.</p>
     *
     * <p>Note that although the value of the property will not change, the value may refer to a mutable object. Calling this method does not guarantee that the value will become immutable.</p>
     *
     * @since 5.0
     */
    void finalizeValue();
}
