/*
 * Copyright 2018 the original author or authors.
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

import java.util.concurrent.Callable;

/**
 * A factory for creating instances of {@link Provider}.
 * <p>
 * An instance of the factory can be injected into a task or plugin by annotating a public constructor or method with {@code javax.inject.Inject}.
 *
 * <pre class='autoTested'>
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
 * <p>An instance of the factory is also available using {@link org.gradle.api.Project#getProviders()}</p>
 *
 * @since 4.0
 */
@Incubating
public interface ProviderFactory {

    /**
     * Creates a {@link Provider} whose value is calculated using the given {@link Callable}.
     *
     * <p>The provider is live and will call the {@link Callable} each time its value is queried. The {@link Callable} may return {@code null}, in which case the provider is considered to have no value.
     *
     * @param value The {@code java.util.concurrent.Callable} use to calculate the value.
     * @return The provider. Never returns null.
     */
    <T> Provider<T> provider(Callable<? extends T> value);
}
