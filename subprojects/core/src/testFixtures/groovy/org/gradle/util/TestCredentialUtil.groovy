/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.util

import org.gradle.internal.credentials.DefaultAwsCredentials
import org.gradle.internal.credentials.DefaultPasswordCredentials

class TestCredentialUtil {

    static DefaultPasswordCredentials defaultPasswordCredentials() {
        return new DefaultPasswordCredentials() {}
    }

    static DefaultPasswordCredentials defaultPasswordCredentials(String username, String password) {
        DefaultPasswordCredentials credentials = defaultPasswordCredentials()
        credentials.setUsername(username);
        credentials.setPassword(password);
        return credentials;
    }

    static DefaultAwsCredentials defaultAwsCredentials() {
        return new DefaultAwsCredentials() {}
    }

    static DefaultAwsCredentials defaultAwsCredentials(String secretValue) {
        def credentials = defaultAwsCredentials()
        credentials.setAccessKey(secretValue)
        credentials.setSecretKey(secretValue)
        credentials.setSessionToken(secretValue)
        return credentials
    }
}
