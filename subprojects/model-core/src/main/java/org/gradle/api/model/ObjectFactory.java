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
import org.gradle.api.reflect.ObjectInstantiationException;

/**
 * A factory for creating various kinds of model objects.
 * <p>
 * An instance of the factory can be injected into a task or plugin by annotating a public constructor or method with {@code javax.inject.Inject}. It is also available via {@link org.gradle.api.Project#getObjects()}.
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
}
