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
     */
    <T extends Named> T named(Class<T> type, String name);
}
