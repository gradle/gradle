/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.artifacts.repositories;

import org.gradle.api.Action;
import org.gradle.api.credentials.Credentials;
import org.gradle.internal.HasInternalProtocol;

/**
 * An artifact repository which supports username/password authentication.
 */
@HasInternalProtocol
public interface AuthenticationSupported {

    /**
     * Returns the username and password credentials used to authenticate to this repository.
     * <p>
     * If no credentials have been assigned to this repository, an empty set of username and password credentials is assigned to this repository and returned.
     * <p>
     * If you are using a different type of credentials than {@link PasswordCredentials}, please use {@link #getCredentials(Class)} to obtain the credentials.
     *
     * @return the credentials
     * @throws IllegalStateException if the credential type was previously set with {@link #credentials(Class, Action)} where the type was not {@link PasswordCredentials}
     */
    PasswordCredentials getCredentials();

    /**
     * Returns the credentials of the specified type used to authenticate with this repository.
     * <p>
     * If no credentials have been assigned to this repository, an empty set of credentials of the specified type is assigned to this repository and returned.
     *
     * @param credentialsType type of the credential
     * @return The credentials
     * @throws IllegalArgumentException when the credentials assigned to this repository are not assignable to the specified type
     */
    <T extends Credentials> T getCredentials(Class<T> credentialsType);

    /**
     * Configures the username and password credentials for this repository using the supplied action.
     * <p>
     * If no credentials have been assigned to this repository, an empty set of username and password credentials is assigned to this repository and passed to the action.
     * <pre class='autoTested'>
     * repositories {
     *     maven {
     *         url "${url}"
     *         credentials {
     *             username = 'joe'
     *             password = 'secret'
     *         }
     *     }
     * }
     * </pre>
     *
     * @throws IllegalStateException when the credentials assigned to this repository are not of type {@link PasswordCredentials}
     */
    void credentials(Action<? super PasswordCredentials> action);

    /**
     * Configures the credentials for this repository using the supplied action.
     * <p>
     * If no credentials have been assigned to this repository, an empty set of credentials of the specified type will be assigned to this repository and given to the configuration action.
     * If credentials have already been specified for this repository, they will be passed to the given configuration action.
     * <pre class='autoTested'>
     * repositories {
     *     maven {
     *         url "${url}"
     *         credentials(AwsCredentials) {
     *             accessKey "myAccessKey"
     *             secretKey "mySecret"
     *         }
     *     }
     * }
     * </pre>
     * <p>
     * The following credential types are currently supported for the {@code credentialsType} argument:
     * <ul>
     * <li>{@link org.gradle.api.artifacts.repositories.PasswordCredentials}</li>
     * <li>{@link org.gradle.api.credentials.AwsCredentials}</li>
     * </ul>
     *
     * @throws IllegalArgumentException if {@code credentialsType} is not of a supported type
     * @throws IllegalArgumentException if {@code credentialsType} is of a different type to the credentials previously specified for this repository
     */
    <T extends Credentials> void credentials(Class<T> credentialsType, Action<? super T> action);

    /**
     * Configures the credentials for this repository that will be provided by the build.
     * <p>
     * Credentials will be provided from Gradle properties based on the repository name.
     * If credentials for this repository can not be resolved and the repository will be used in the current build, then the build will fail to start and point to the missing configuration.
     * <pre class='autoTested'>
     * repositories {
     *     maven {
     *         url "${url}"
     *         credentials(PasswordCredentials)
     *     }
     * }
     * </pre>
     * <p>
     * The following credential types are currently supported for the {@code credentialsType} argument:
     * <ul>
     * <li>{@link org.gradle.api.credentials.PasswordCredentials}</li>
     * <li>{@link org.gradle.api.credentials.AwsCredentials}</li>
     * <li>{@link org.gradle.api.credentials.HttpHeaderCredentials}</li>
     * </ul>
     *
     * @throws IllegalArgumentException if {@code credentialsType} is not of a supported type
     *
     * @since 6.6
     */
    void credentials(Class<? extends Credentials> credentialsType);

    /**
     * <p>Configures the authentication schemes for this repository.
     *
     * <p>This method executes the given action against the {@link AuthenticationContainer} for this project. The {@link
     * AuthenticationContainer} is passed to the closure as the closure's delegate.
     * <p>
     * If no authentication schemes have been assigned to this repository, a default set of authentication schemes are used based on the repository's transport scheme.
     *
     * <pre class='autoTested'>
     * repositories {
     *     maven {
     *         url "${url}"
     *         authentication {
     *             basic(BasicAuthentication)
     *         }
     *     }
     * }
     * </pre>
     * <p>
     * Supported authentication scheme types extend {@link org.gradle.authentication.Authentication}.
     *
     * @param action the action to use to configure the authentication schemes.
     */
    void authentication(Action<? super AuthenticationContainer> action);

    /**
     * Returns the authentication schemes for this repository.
     *
     * @return the authentication schemes for this repository
     */
    AuthenticationContainer getAuthentication();
}
