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

import org.gradle.api.Action;
import org.gradle.api.NonExtensible;
import org.gradle.api.credentials.AwsCredentials;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.file.FileContents;
import org.gradle.api.file.RegularFile;
import org.gradle.api.initialization.Settings;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.process.ExecOutput;
import org.gradle.process.ExecSpec;
import org.gradle.process.JavaExecSpec;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;

/**
 * A factory for creating instances of {@link Provider}.
 *
 * <p>An instance of the factory can be injected into a task, plugin or other object by annotating a public constructor or property getter method with {@code javax.inject.Inject}.
 * It is also available via {@link org.gradle.api.Project#getProviders()} and {@link Settings#getProviders()}.
 *
 * @since 4.0
 */
@NonExtensible
@ServiceScope(Scope.Build.class)
public interface ProviderFactory {

    /**
     * Creates a {@link Provider} whose value is calculated using the given {@link Callable}.
     *
     * <p>The provider is live and will call the {@link Callable} each time its value is queried. The {@link Callable} may return {@code null}, in which case the provider is considered to have no value.
     *
     * @param value The {@code java.util.concurrent.Callable} use to calculate the value.
     * @return The provider. Never returns null.
     */
    <T> Provider<T> provider(Callable<? extends @Nullable T> value);

    /**
     * Creates a {@link Provider} whose value is fetched from the environment variable with the given name.
     *
     * @param variableName The name of the environment variable.
     * @return The provider. Never returns null.
     * @since 6.1
     */
    Provider<String> environmentVariable(String variableName);

    /**
     * Creates a {@link Provider} whose value is fetched from the environment variable with the given name.
     *
     * @param variableName The provider for the name of the environment variable; when the given provider has no value, the returned provider has no value.
     * @return The provider. Never returns null.
     * @since 6.1
     */
    Provider<String> environmentVariable(Provider<String> variableName);

    /**
     * Creates a {@link Provider} whose value is a name-to-value map of the environment variables with the names starting with the given prefix.
     * The prefix comparison is case-sensitive. The returned map is immutable.
     *
     * @param variableNamePrefix The prefix of the environment variable names
     * @return The provider. Never returns null.
     * @since 7.5
     */
    Provider<Map<String, String>> environmentVariablesPrefixedBy(String variableNamePrefix);

    /**
     * Creates a {@link Provider} whose value is a name-to-value map of the environment variables with the names starting with the given prefix.
     * The prefix comparison is case-sensitive. The returned map is immutable.
     *
     * @param variableNamePrefix The prefix of the environment variable names
     * @return The provider. Never returns null.
     * @since 7.5
     */
    Provider<Map<String, String>> environmentVariablesPrefixedBy(Provider<String> variableNamePrefix);

    /**
     * Creates a {@link Provider} whose value is fetched from system properties using the given property name.
     *
     * @param propertyName the name of the system property
     * @return the provider for the system property, never returns null
     * @since 6.1
     */
    Provider<String> systemProperty(String propertyName);

    /**
     * Creates a {@link Provider} whose value is fetched from system properties using the given property name.
     *
     * @param propertyName the name of the system property
     * @return the provider for the system property, never returns null
     * @since 6.1
     */
    Provider<String> systemProperty(Provider<String> propertyName);

    /**
     * Creates a {@link Provider} whose value is a name-to-value map of the system properties with the names starting with the given prefix.
     * The prefix comparison is case-sensitive. The returned map is immutable.
     *
     * @param variableNamePrefix The prefix of the system property names
     * @return The provider. Never returns null.
     * @since 7.5
     */
    Provider<Map<String, String>> systemPropertiesPrefixedBy(String variableNamePrefix);

    /**
     * Creates a {@link Provider} whose value is a name-to-value map of the system properties with the names starting with the given prefix.
     * The prefix comparison is case-sensitive. The returned map is immutable.
     *
     * @param variableNamePrefix The prefix of the system property names
     * @return The provider. Never returns null.
     * @since 7.5
     */
    Provider<Map<String, String>> systemPropertiesPrefixedBy(Provider<String> variableNamePrefix);

    /**
     * Creates a {@link Provider} whose value is fetched from the Gradle property of the given name.
     *
     * @param propertyName the name of the Gradle property
     * @return the provider for the Gradle property, never returns null
     * @since 6.2
     */
    Provider<String> gradleProperty(String propertyName);

    /**
     * Creates a {@link Provider} whose value is fetched from the Gradle property of the given name.
     *
     * @param propertyName the name of the Gradle property
     * @return the provider for the Gradle property, never returns null
     * @since 6.2
     */
    Provider<String> gradleProperty(Provider<String> propertyName);

    /**
     * Creates a {@link Provider} whose value is a name-to-value map of the Gradle properties with the names starting with the given prefix.
     * The prefix comparison is case-sensitive. The returned map is immutable.
     *
     * @param variableNamePrefix The prefix of the Gradle property names
     * @return The provider. Never returns null.
     * @since 8.0
     */
    Provider<Map<String, String>> gradlePropertiesPrefixedBy(String variableNamePrefix);

    /**
     * Creates a {@link Provider} whose value is a name-to-value map of the Gradle properties with the names starting with the given prefix.
     * The prefix comparison is case-sensitive. The returned map is immutable.
     *
     * @param variableNamePrefix The prefix of the Gradle property names
     * @return The provider. Never returns null.
     * @since 8.0
     */
    Provider<Map<String, String>> gradlePropertiesPrefixedBy(Provider<String> variableNamePrefix);

    /**
     * Allows lazy access to the contents of the given file.
     *
     * <p>When the file contents are read at configuration time the file is automatically considered
     * as an input to the configuration model.
     *
     * @param file the file whose contents to read.
     * @return an interface that allows lazy access to the contents of the given file.
     *
     * @see FileContents#getAsText()
     * @see FileContents#getAsBytes()
     *
     * @since 6.1
     */
    FileContents fileContents(RegularFile file);

    /**
     * Allows lazy access to the contents of the given file.
     *
     * <p>When the file contents are read at configuration time the file is automatically considered
     * as an input to the configuration model.
     *
     * @param file provider of the file whose contents to read.
     * @return an interface that allows lazy access to the contents of the given file.
     *
     * @see FileContents#getAsText()
     * @see FileContents#getAsBytes()
     *
     * @since 6.1
     */
    FileContents fileContents(Provider<RegularFile> file);

    /**
     * Allows lazy access to the output of the external process.
     *
     * <p>When the process output is read at configuration time it is considered as an input to the
     * configuration model. Consequent builds will re-execute the process to obtain the output and
     * check if the cached model is still up-to-date.
     *
     * <p>The process input and output streams cannot be configured.
     *
     * <p>For more sophisticated use cases, like handling execution exceptions, supplying input streams or processing the output in a streaming way, use {@link ValueSource}.
     *
     * @param action the configuration of the external process with the output stream
     * pre-configured.
     * @return an interface that allows lazy access to the process' output.
     *
     * @since 7.5
     */
    ExecOutput exec(Action<? super ExecSpec> action);

    /**
     * Allows lazy access to the output of the external java process.
     *
     * <p>When the process output is read at configuration time it is considered as an input to the
     * configuration model. Consequent builds will re-execute the process to obtain the output and
     * check if the cached model is still up-to-date.
     *
     * <p>The process input and output streams cannot be configured.
     *
     * <p>For more sophisticated use cases, like handling execution exceptions, supplying input streams or processing the output in a streaming way, use {@link ValueSource}.
     *
     * @param action the configuration of the external process with the output stream
     * pre-configured.
     * @return an interface that allows lazy access to the process' output.
     *
     * @since 7.5
     */
    ExecOutput javaexec(Action<? super JavaExecSpec> action);

    /**
     * Creates a {@link Provider} whose value is obtained from the given {@link ValueSource}.
     *
     * @param valueSourceType the type of the {@link ValueSource}
     * @param configuration action to configure the parameters to the given {@link ValueSource}
     * @return the provider, never returns null
     * @since 6.1
     */
    <T, P extends ValueSourceParameters>
    Provider<T> of(
        Class<? extends ValueSource<T, P>> valueSourceType,
        Action<? super ValueSourceSpec<P>> configuration
    );

    /**
     * Creates a {@link Provider} for the given {@link Credentials} type.
     *
     * <p>The provider returned by this method should be attached to a task's input property.
     * This way, the presence of credentials will be validated before any of the tasks are executed if and only if the task with credentials property is to be executed.
     *
     * <p>Values for the requested Credentials type will be sourced from the project's properties using the pattern "identity" + credentials field.
     * For example, {@link PasswordCredentials} provider with identity "myService" will look for properties named "myServiceUsername" and "myServicePassword".
     *
     * <p>The following credential types are currently supported:
     * <ul>
     * <li>{@link PasswordCredentials}</li>
     * <li>{@link AwsCredentials}</li>
     * </ul>
     *
     * @param credentialsType type of credentials to be provided.
     * @param identity identity to be associated with the credentials.
     * @return The provider. Never returns null.
     *
     * @since 6.6
     */
    <T extends Credentials> Provider<T> credentials(Class<T> credentialsType, String identity);

    /**
     * Creates a {@link Provider} for the given {@link Credentials} type.
     *
     * <p>The provider returned by this method should be attached to a task's input property.
     * This way, the presence of credentials will be validated before any of the tasks are executed if and only if the task with credentials property is to be executed.
     *
     * <p>Values for the requested Credentials type will be sourced from the project's properties using the pattern "identity" + credentials field.
     * For example, {@link PasswordCredentials} provider with identity "myService" will look for properties named "myServiceUsername" and "myServicePassword".
     *
     * <p>The following credential types are currently supported:
     * <ul>
     * <li>{@link PasswordCredentials}</li>
     * <li>{@link AwsCredentials}</li>
     * </ul>
     *
     * @param credentialsType type of credentials to be provided.
     * @param identity a provider returning the identity to be associated with the credentials.
     * @return The provider. Never returns null.
     *
     * @since 6.6
     */
    <T extends Credentials> Provider<T> credentials(Class<T> credentialsType, Provider<String> identity);

    /**
     * Returns a provider which value will be computed by combining a provider value with another
     * provider value using the supplied combiner function.
     *
     * <p>The resulting provider will be live, so that each time it is queried, it queries both supplied providers
     * and applies the combiner to the results. Whenever any of the providers has no value, the new provider
     * will also have no value and the combiner will not be called.
     *
     * <p>If the supplied providers represents a task or the output of a task, the resulting provider
     * will carry the dependency information.
     *
     * @param first the first provider to combine with
     * @param second the second provider to combine with
     * @param combiner the combiner of values. May return {@code null}, in which case the provider
     * will have no value.
     * @param <A> the type of the first provider
     * @param <B> the type of the second provider
     * @param <R> the type of the result of the combiner
     * @return a combined provider
     * @since 6.6
     */
    <A, B, R> Provider<R> zip(Provider<A> first, Provider<B> second, BiFunction<? super A, ? super B, ? extends R> combiner);
}
