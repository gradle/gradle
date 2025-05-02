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
package org.gradle.internal.resource.transport.http;


import com.google.common.base.Preconditions;
import org.gradle.api.credentials.Credentials;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

public interface HttpProxySettings {

    HttpProxy getProxy();

    class HttpProxy {
        public final String host;
        public final int port;
        public final HttpProxyCredentials credentials;

        public HttpProxy(String host, int port, String username, String password) {
            this.host = host;
            this.port = port;
            if (username == null || username.isEmpty()) {
                credentials = null;
            } else {
                credentials = new HttpProxyCredentials(username, password);
            }
        }
    }

    @NullMarked
    class HttpProxyCredentials implements Credentials {
        private final String username;
        private final String password;

        public HttpProxyCredentials(String username, @Nullable String password) {
            this.username = Preconditions.checkNotNull(username);
            this.password = password;
        }

        public String getUsername() {
            return username;
        }

        @Nullable
        public String getPassword() {
            return password;
        }
    }
}
