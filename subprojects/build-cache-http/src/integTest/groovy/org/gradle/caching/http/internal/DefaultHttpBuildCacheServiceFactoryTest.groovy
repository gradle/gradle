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

package org.gradle.caching.http.internal

import spock.lang.Specification

import static org.gradle.caching.http.internal.DefaultHttpBuildCacheServiceFactory.extractCredentialsFromUserInfo

class DefaultHttpBuildCacheServiceFactoryTest extends Specification {

    def "extract username and password from userinfo #userinfo"() {
        when:
        def credentials = extractCredentialsFromUserInfo(new URI("https://${userinfo}@myserver.local"))

        then:
        credentials.username == username
        credentials.password == password

        where:
        userinfo                             | username   | password
        'user:password'                      | 'user'     | 'password'
        'user:pas%23:%5D()'                  | 'user'     | 'pas#:]()'
        'us%5B%5B%23%3B%C3%BCr:pas%23:%5D()' | 'us[[#;ür' | 'pas#:]()'
        'user'                               | null       | null
    }

    def "username cannot contain colon"() {
        // Known limitation. The user should use the DSL
        when:
        def credentials = extractCredentialsFromUserInfo(new URI("https://us%3Aer:password@myserver.local"))

        then:
        credentials.username == 'us'
        credentials.password == 'er:password'
    }
}
