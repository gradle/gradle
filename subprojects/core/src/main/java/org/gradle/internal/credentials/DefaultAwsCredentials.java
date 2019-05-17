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

package org.gradle.internal.credentials;

import org.gradle.api.credentials.AwsCredentials;

public class DefaultAwsCredentials implements AwsCredentials {

    private String accessKey;
    private String secretKey;
    private String sessionToken;

    @Override
    public String getAccessKey() {
        return accessKey;
    }

    @Override
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    @Override
    public String getSecretKey() {
        return secretKey;
    }

    @Override
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    @Override
    public String getSessionToken() {
        return sessionToken;
    }

    @Override
    public void setSessionToken(String token) {
        this.sessionToken = token;
    }
}
