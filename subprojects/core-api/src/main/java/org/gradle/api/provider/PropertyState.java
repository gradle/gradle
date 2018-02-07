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
 * <p><b>Note:</b> This interface is not intended for implementation by build script or plugin authors. An instance of this class can be created
 * through the factory methods {@link org.gradle.api.Project#property(java.lang.Class)} or {@link org.gradle.api.provider.ProviderFactory#property(java.lang.Class)}.
 *
 * @param <T> Type of value represented by property state
 * @since 4.0
 * @deprecated Use {@link Property} instead.
 */

@Incubating
@Deprecated
public interface PropertyState<T> extends Property<T> {
}
