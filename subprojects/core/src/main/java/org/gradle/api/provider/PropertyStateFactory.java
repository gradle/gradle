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
 * A factory for creating instances of {@code PropertyState}.
 * <p>
 * An instance of the factory can be injected by annotating a public constructor or method with {@code javax.inject.Inject}.
 *
 * <pre autoTested=''>
 * public class MyTask extends DefaultTask {
 *   // injection into a constructor
 *   @Inject
 *   public MyTask(PropertyStateFactory propertyStateFactory) { ... }
 *
 *   // injection into a method
 *   @Inject
 *   public PropertyStateFactory getPropertyStateFactory() {
 *     throw new UnsupportedOperationException();
 *   }
 * }
 * </pre>
 *
 * @since 3.5
 */
@Incubating
public interface PropertyStateFactory {

    <T> ConfigurablePropertyState<T> property(Class<T> clazz);
}
