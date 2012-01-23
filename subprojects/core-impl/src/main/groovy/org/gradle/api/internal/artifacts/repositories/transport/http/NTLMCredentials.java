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
package org.gradle.api.internal.artifacts.repositories.transport.http;

import org.gradle.api.artifacts.repositories.PasswordCredentials;

class NTLMCredentials {
    private final String domain;
    private final String username;
    private final String password;

    public NTLMCredentials(PasswordCredentials credentials) {
        String domain;
        String username = credentials.getUsername();
        int slashPos = username.indexOf('\\');
        if (slashPos >= 0) {
            domain = username.substring(0, slashPos);
            username = username.substring(slashPos + 1);
        } else {
            domain = System.getProperty("http.auth.ntlm.domain");
        }
        this.domain = domain == null ? null : domain.toUpperCase();
        this.username = username;
        this.password = credentials.getPassword();
    }

    public String getDomain() {
        return domain;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getWorkstation() {
        return null;
    }
}
