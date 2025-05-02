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

package org.gradle.test.fixtures.server.http

import org.eclipse.jetty.security.Authenticator
import org.eclipse.jetty.security.ServerAuthException
import org.eclipse.jetty.server.Authentication

import javax.servlet.ServletRequest
import javax.servlet.ServletResponse

class TestHttpHeaderAuthenticator implements Authenticator {

    public static final String AUTH_SCHEME_NAME = "HEADER"

    @Override
    void setConfiguration(AuthConfiguration configuration) {

    }

    @Override
    String getAuthMethod() {
        AUTH_SCHEME_NAME
    }

    @Override
    void prepareRequest(ServletRequest request) {

    }

    @Override
    Authentication validateRequest(ServletRequest request, ServletResponse response, boolean mandatory) throws ServerAuthException {
        Authentication.SEND_CONTINUE
    }

    @Override
    boolean secureResponse(ServletRequest request, ServletResponse response, boolean mandatory, Authentication.User validatedUser) throws ServerAuthException {
        return false
    }
}
