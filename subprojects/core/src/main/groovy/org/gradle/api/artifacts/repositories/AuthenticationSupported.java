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
import org.gradle.credentials.Credentials;

/**
 * An artifact repository which supports username/password authentication.
 */
public interface AuthenticationSupported {

    /**
     * Returns the standard username and password credentials used to authenticate to this repository.
     * @return The PasswordCredentials
     */
    PasswordCredentials getCredentials();

    /**
     * Returns the alternative credentials used to authenticate with this repository.
     * Alternative credentials are used for non username/password credentials.
     * @return The Credentials
     */
    Credentials getAlternativeCredentials();

    /**
     * Configure the credentials for this repository using the supplied Closure.
     *
     * <pre autoTested=''>
     * repositories {
     *     maven {
     *         credentials {
     *             username = 'joe'
     *             password = 'secret'
     *         }
     *     }
     * }
     * </pre>
     */
    void credentials(Closure closure);

    /**
     * Configure the credentials for this repository using the supplied action.
     *
     * <pre autoTested=''>
     * repositories {
     *     maven {
     *         credentials {
     *             username = 'joe'
     *             password = 'secret'
     *         }
     *     }
     * }
     * </pre>
     */
    void credentials(Action<? super PasswordCredentials> action);

    /**
     * Configures strongly typed credentials for this repository using the supplied action.
     *
     * repositories {
     *    maven {
     *        url "${url}"
     *        credentials(AwsCredentials) {
     *            accessKey "myAccessKey"
     *            secretKey "mySecret"
     *        }
     *    }
     *  }
     *
     */
    <T extends Credentials> void credentials(Class<T> clazz, Action<? super Credentials> action);
}
