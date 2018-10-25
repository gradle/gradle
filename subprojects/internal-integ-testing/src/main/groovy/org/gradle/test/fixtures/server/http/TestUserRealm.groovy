/*
 * Copyright 2017 the original author or authors.
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

import org.eclipse.jetty.security.AbstractLoginService
import org.eclipse.jetty.util.security.Password

class TestUserRealm extends AbstractLoginService {
    String username
    String password

    TestUserRealm() {
        setName("test")
    }

    @Override
    protected String[] loadRoleInfo(UserPrincipal user) {
        return AuthScheme.AuthSchemeHandler.ROLES
    }

    @Override
    protected UserPrincipal loadUserInfo(String username) {
        if (username == this.username) {
            return new UserPrincipal(username, new Password(password))
        }
        return null
    }
}
