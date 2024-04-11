/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.infra

import spock.lang.Specification

class EnvironmentVariablesPropagationTest extends Specification {
    def "no sensitive env variables #value propagated to test JVMs"() {
        expect:
        System.getenv().each { k, v ->
            assert !k.toLowerCase().contains(value.toLowerCase())
            assert !v.toLowerCase().contains(value.toLowerCase())
        }

        where:
        value << ['GRADLE_ENTERPRISE_ACCESS_KEY',
                  "api_key",
                  "access_key",
                  "apikey",
                  "accesskey",
                  "password",
                  "token",
                  "credential",
                  "auth"]
    }
}
