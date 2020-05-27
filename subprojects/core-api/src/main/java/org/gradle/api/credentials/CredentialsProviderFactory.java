/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.credentials;

import org.gradle.api.Incubating;
import org.gradle.api.provider.Provider;

import java.util.function.Supplier;

/**
 *
 * A factory for creating instances of managed {@link Provider} objects for {@link Credentials} types.
 *
 * <p>
 * The providers that this factory returns should be attached to a task's input properties.
 * This way, the presence of credentials will be validated before any of the tasks are executed if and only if the task with credentials property is to be executed.
 *
 * <p>
 * Values for the requested Credentials type will be sourced from the project's properties using the pattern "identity" + credentials field.
 * For example, {@link PasswordCredentials} provider with identity "myService" will look for properties named "myServiceUsername" and "myServicePassword".
 *
 * An instance of the factory can be injected into a task, plugin or other object by annotating a public constructor or property getter method with {@code javax.inject.Inject}.
 * It is also available via {@link org.gradle.api.Project#getCredentials()}.
 *
 * <p>
 * The following credential types are currently supported:
 * <ul>
 * <li>{@link PasswordCredentials}</li>
 * <li>{@link AwsCredentials}</li>
 * </ul>
 *
 * @since 6.6
 */
@Incubating
public interface CredentialsProviderFactory {

    /**
     * Creates a {@link Provider} for the given {@link Credentials} type.
     *
     * @param credentialsType type of credentials to be provided.
     * @param identity identity to be associated with the credentials.
     * @return The provider. Never returns null.
     */
    <T extends Credentials> Provider<T> provideCredentials(Class<T> credentialsType, String identity);

    /**
     * Creates a {@link Provider} for the given {@link Credentials} type.
     *
     * @param credentialsType type of credentials to be provided.
     * @param identity a function returning the identity to be associated with the credentials.
     * @return The provider. Never returns null.
     */
    <T extends Credentials> Provider<T> provideCredentials(Class<T> credentialsType, Supplier<String> identity);

}
