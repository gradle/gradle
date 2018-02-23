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

package org.gradle.test.fixtures.server.http;

import org.mortbay.jetty.Request;
import org.mortbay.jetty.security.Password;
import org.mortbay.jetty.security.UserRealm;

import java.security.Principal;

class TestUserRealm implements UserRealm {
    String username
    String password

    Principal authenticate(String username, Object credentials, Request request) {
        Password passwordCred = new Password(password)
        if (username == this.username && passwordCred.check(credentials)) {
            return getPrincipal(username)
        }
        return null
    }

    String getName() {
        return "test"
    }

    Principal getPrincipal(String username) {
        return new Principal() {
            String getName() {
                return username
            }
        }
    }

    boolean reauthenticate(Principal user) {
        return false
    }

    boolean isUserInRole(Principal user, String role) {
        return false
    }

    void disassociate(Principal user) {
    }

    Principal pushRole(Principal user, String role) {
        return user
    }

    Principal popRole(Principal user) {
        return user
    }

    void logout(Principal user) {
    }
}
