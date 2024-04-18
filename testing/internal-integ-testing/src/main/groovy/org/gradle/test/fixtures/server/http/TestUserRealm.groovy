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

import groovy.transform.CompileStatic
import org.eclipse.jetty.security.HashLoginService
import org.eclipse.jetty.security.UserStore
import org.eclipse.jetty.util.security.Credential

@CompileStatic
class TestUserRealm extends HashLoginService {
    public static final String REALM_NAME = 'test'
    public static final String ROLE = 'test'
    private final UserStore userStore

    final String username
    final String password

    TestUserRealm(String username, String password) {
        super(REALM_NAME)
        userStore = new UserStore()
        setUserStore(userStore)
        this.username = username
        this.password = password
        userStore.addUser(username, Credential.getCredential(password), [ROLE] as String[])
    }

}
