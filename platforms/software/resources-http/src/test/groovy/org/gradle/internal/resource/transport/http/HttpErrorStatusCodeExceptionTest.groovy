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

package org.gradle.internal.resource.transport.http

import spock.lang.Specification

class HttpErrorStatusCodeExceptionTest extends Specification {

    def "can identify status code as 5xx error"() {
        when:
        boolean serverError = new HttpErrorStatusCodeException('GET', 'http://localhost:8080/', statusCode, '')

        then:
        serverError

        where:
        statusCode << (500..599).collect { it }
    }

    def "can identify status code as non-server error"() {
        when:
        boolean serverError = new HttpErrorStatusCodeException('GET', 'http://localhost:8080/', statusCode, '')

        then:
        serverError

        where:
        statusCode << [499, 600]
    }
}
