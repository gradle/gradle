/*
 * Copyright 2014 the original author or authors.
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

/**
 * Represents credentials used to authenticate with Amazon Web Services.
 */
@Incubating
public interface AwsCredentials extends Credentials {

    /**
     * Returns the access key to use to authenticate with AWS.
     */
    String getAccessKey();

    /**
     * Sets the access key to use to authenticate with AWS.
     */
    void setAccessKey(String accessKey);

    /**
     * Returns the secret key to use to authenticate with AWS.
     */
    String getSecretKey();

    /**
     * Sets the secret key to use to authenticate with AWS.
     */
    void setSecretKey(String secretKey);

    /**
     * Returns the secret key to use to authenticate with AWS.
     *
     * @since 3.3
     */
    String getSessionToken();

    /**
     * Sets the secret key to use to authenticate with AWS.
     *
     * @since 3.3
     */
    void setSessionToken(String token);

}
