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
import org.gradle.api.Project;

import java.util.concurrent.Callable;

/**
 * A factory for creating instances of {@code Provider} and {@code PropertyState}.
 * <p>
 * An instance of the factory can be injected into a task or plugin by annotating a public constructor or method with {@code javax.inject.Inject}.
 *
 * <pre autoTested=''>
 * public class MyTask extends DefaultTask {
 *   // injection into a constructor
 *   {@literal @}javax.inject.Inject
 *   public MyTask(ProviderFactory providerFactory) { }
 *
 *   // injection into a method
 *   {@literal @}javax.inject.Inject
 *   public ProviderFactory getProviderFactory() {
 *     throw new UnsupportedOperationException();
 *   }
 * }
 * </pre>
 *
 * <p>An instance of the factory is also available using {@link Project#getProviders()}</p>
 *
 * @since 4.0
 */
@Incubating
public interface ProviderFactory {

    /**
     * Creates a {@code Provider} implementation based on the provided value.
     *
     * @param value The {@code java.util.concurrent.Callable} use to calculate the value.
     * @return The provider. Never returns null.
     * @throws org.gradle.api.InvalidUserDataException If the provided value is null.
     */
    <T> Provider<T> provider(Callable<T> value);

    /**
     * Creates a {@code PropertyState} implementation based on the provided class.
     * The value returned by the property state is the default value of the standard Java data type.
     * Please see <a href="https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html">Oracle's Java manual</a> for more information.
     * <p>
     * Any other data type than the standard Java data types returns null as default value.
     *
     * @param clazz The class to be used for property state.
     * @return The property state. Never returns null.
     * @throws org.gradle.api.InvalidUserDataException If the provided class is null.
     */
    <T> PropertyState<T> property(Class<T> clazz);
}
