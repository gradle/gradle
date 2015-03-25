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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.credentials.Credentials;

/**
 * An artifact repository which supports username/password authentication.
 */
public interface AuthenticationSupported {

    /**
     * Returns the standard username and password credentials used to authenticate to this repository.
     *
     * @return The PasswordCredentials
     */
    PasswordCredentials getCredentials();

    /**
     * Returns the credentials of the specified type used to authenticate with this repository.
     * Instantiates Credentials if not done already.
     *
     * @return The Credentials
     */
    @Incubating
    public <T extends Credentials> T getCredentials(Class<T> clazz);

    /**
     * Returns the alternative credentials used to authenticate with this repository.
     *
     * @return The Credentials
     */
    @Incubating
    Credentials getAlternativeCredentials();

    /**
     * Configures the {@link PasswordCredentials} for this repository using the supplied Closure.
     *
     * <pre autoTested=''>
     * repositories {
     *     maven {
     *         credentials {
     *              username = 'joe'
     *              password = 'secret'
     *         }
     *     }
     * }
     * </pre>
     *
     * @throws IllegalStateException when credentials are not of type PasswordCredentials.
     */
    void credentials(Closure closure);

    /**
     * Configure the credentials for this repository using the supplied action.
     *
     * <pre autoTested=''>
     *     repositories {
     *         maven {
     *             url "${url}"
     *             credentials {
     *                 username = 'joe'
     *                 password = 'secret'
     *             }
     *         }
     *     }
     * </pre>
     *
     * @throws IllegalStateException when credentials are not of type PasswordCredentials.
     */
    void credentials(Action<? super PasswordCredentials> action);

    /**
     * Configures strongly typed credentials for this repository using the supplied action.
     *
     * repositories {
     *     maven {
     *         url "${url}"
     *         credentials(AwsCredentials) {
     *             accessKey "myAccessKey"
     *             secretKey "mySecret"
     *         }
     *     }
     * }
     *
     * @throws IllegalStateException if passed credentials type is not assignable to an already set credentials type.
     */
    @Incubating
    <T extends Credentials> void credentials(Class<T> clazz, Action<? super T> action) throws IllegalStateException;
}
