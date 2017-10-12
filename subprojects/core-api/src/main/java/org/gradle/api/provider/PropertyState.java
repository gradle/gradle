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

/**
 * A {@code Provider} representation for capturing the state of a property. The value can be provided by using the method {@link #set(Object)} or {@link #set(Provider)}.
 *
 * <p>
 *  The typical usage pattern is to expose a single getter returning the {@link PropertyState}. This allows clients to mutate and query the current value. It also
 *  allows them to chain it as an input to another {@link PropertyState}'s {@link #set(Provider)} method. For instance, a task can be wired to take a configuration
 *  value from an extension before that extension has been configured by the user's build script. The actual value is only queried when the task executes.
 * </p>
 *
 * <p>
 *  There is no need to provide additional convenience methods on an object with a getter of type {@link PropertyState}. The DSL decoration will add convenience
 *  setters automatically.
 * </p>
 *
 * <p>
 *  Do not use <code>PropertyState&lt;File&gt;</code>. Use {@link org.gradle.api.file.DirectoryVar} or {@link org.gradle.api.file.RegularFileVar} instead.
 * </p>
 *
 * <p>
 *  <b>Note:</b> This interface is not intended for implementation by build script or plugin authors. An instance of this class can be created
 *  through the factory methods {@link org.gradle.api.Project#property(java.lang.Class)} or
 *  {@link org.gradle.api.provider.ProviderFactory#property(java.lang.Class)}.
 * </p>
 *
 * @param <T> Type of value represented by property state
 * @since 4.0
 * @deprecated Use {@link Property} instead.
 */
@Incubating
@Deprecated
public interface PropertyState<T> extends Property<T> {
}
