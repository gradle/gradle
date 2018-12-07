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

package org.gradle.api.model;

import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.reflect.ObjectInstantiationException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A factory for creating various kinds of model objects.
 * <p>
 * An instance of the factory can be injected into a task or plugin by annotating a public constructor or property getter method with {@code javax.inject.Inject}. It is also available via {@link org.gradle.api.Project#getObjects()}.
 *
 * @since 4.0
 */
@Incubating
public interface ObjectFactory {
    /**
     * Creates a simple immutable {@link Named} object of the given type and name.
     *
     * <p>The given type can be an interface that extends {@link Named} or an abstract class that 'implements' {@link Named}. An abstract class, if provided:</p>
     * <ul>
     *     <li>Must provide a zero-args constructor that is not private.</li>
     *     <li>Must not define or inherit any instance fields.</li>
     *     <li>Should not provide an implementation for {@link Named#getName()} and should define this method as abstract. Any implementation will be overridden.</li>
     *     <li>Must not define or inherit any other abstract methods.</li>
     * </ul>
     *
     * <p>An interface, if provided, must not define or inherit any other methods.</p>
     *
     * <p>Objects created using this method are not decorated or extensible.</p>
     *
     * @throws ObjectInstantiationException On failure to create the new instance.
     * @since 4.0
     */
    <T extends Named> T named(Class<T> type, String name) throws ObjectInstantiationException;

    /**
     * Create a new instance of T, using {@code parameters} as the construction parameters.
     *
     * <p>The type must be a non-abstract class.</p>
     *
     * <p>Objects created using this method are decorated and extensible, meaning that they have DSL support mixed in and can be extended using the `extensions` property, similar to the {@link org.gradle.api.Project} object.</p>
     *
     * <p>An @Inject annotation is required on any constructor that accepts parameters because JSR-330 semantics for dependency injection are used. In addition to those parameters provided as an argument to this method, the following services are also available for injection:</p>
     *
     * <ul>
     *     <li>{@link ObjectFactory}.</li>
     *     <li>{@link org.gradle.api.file.ProjectLayout}.</li>
     *     <li>{@link org.gradle.api.provider.ProviderFactory}.</li>
     * </ul>
     *
     * @throws ObjectInstantiationException On failure to create the new instance.
     * @since 4.2
     */
    <T> T newInstance(Class<? extends T> type, Object... parameters) throws ObjectInstantiationException;

    /**
     * Creates a {@link SourceDirectorySet}.
     *
     * @param name A short name for the set.
     * @param displayName A human consumable display name for the set.
     * @since 5.0
     */
    SourceDirectorySet sourceDirectorySet(String name, String displayName);

    /**
     * Creates a {@link Property} implementation to hold values of the given type. The property has no initial value.
     *
     * <p>For certain types, there are more specialized property factory methods available:</p>
     * <ul>
     * <li>For {@link List} properties, you should use {@link #listProperty(Class)}.</li>
     * <li>For {@link Set} properties, you should use {@link #setProperty(Class)}.</li>
     * <li>For {@link Map} properties, you should use {@link #mapProperty(Class, Class)}.</li>
     * <li>For {@link org.gradle.api.file.Directory} properties, you should use {@link #directoryProperty()}.</li>
     * <li>For {@link org.gradle.api.file.RegularFile} properties, you should use {@link #fileProperty()}.</li>
     * </ul>
     *
     * @param valueType The type of the property.
     * @return The property. Never returns null.
     * @since 4.3
     */
    <T> Property<T> property(Class<T> valueType);

    /**
     * Creates a {@link ListProperty} implementation to hold a {@link List} of the given element type {@code T}. The property has an empty list as its initial value.
     *
     * <p>The implementation will return immutable {@link List} values from its query methods.</p>
     *
     * @param elementType The type of element.
     * @param <T> The type of element.
     * @return The property. Never returns null;
     * @since 4.3
     */
    <T> ListProperty<T> listProperty(Class<T> elementType);

    /**
     * Creates a {@link SetProperty} implementation to hold a {@link Set} of the given element type {@code T}. The property has an empty set as its initial value.
     *
     * <p>The implementation will return immutable {@link Set} values from its query methods.</p>
     *
     * @param elementType The type of element.
     * @param <T> The type of element.
     * @return The property. Never returns null;
     * @since 4.5
     */
    <T> SetProperty<T> setProperty(Class<T> elementType);

    /**
     * Creates a {@link MapProperty} implementation to hold a {@link Map} of the given key type {@code K} and value type {@code V}. The property has an empty map as its initial value.
     *
     * <p>The implementation will return immutable {@link Map} values from its query methods.</p>
     * @param keyType the type of key.
     * @param valueType the type of value.
     * @param <K> the type of key.
     * @param <V> the type of value.
     * @return the property. Never returns null.
     * @since 5.1
     */
    <K, V> MapProperty<K, V> mapProperty(Class<K> keyType, Class<V> valueType);

    /**
     * Creates a new {@link DirectoryProperty} that uses the project directory to resolve relative paths, if required. The property has no initial value.
     *
     * @since 5.0
     */
    DirectoryProperty directoryProperty();

    /**
     * Creates a new {@link RegularFileProperty} that uses the project directory to resolve relative paths, if required. The property has no initial value.
     *
     * @since 5.0
     */
    RegularFileProperty fileProperty();
}
