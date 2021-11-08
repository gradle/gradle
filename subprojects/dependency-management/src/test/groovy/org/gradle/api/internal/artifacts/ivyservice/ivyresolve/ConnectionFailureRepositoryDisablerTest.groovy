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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.internal.resource.transport.http.HttpErrorStatusCodeException
import spock.lang.Specification
import spock.lang.Subject

class ConnectionFailureRepositoryDisablerTest extends Specification {

    @Subject RepositoryDisabler disabler = new ConnectionFailureRepositoryDisabler()

    def "disables repository for critical exception [#exception]"() {
        given:
        def repositoryId1 = 'abc'
        def repositoryId2 = 'def'

        when:
        boolean disabled = disabler.disableRepository(repositoryId1, exception)

        then:
        disabled
        disabler.disabledRepositories.size() == 1
        disabler.disabledRepositories.contains(repositoryId1)

        when:
        disabled = disabler.disableRepository(repositoryId1, exception)

        then:
        disabled
        disabler.disabledRepositories.size() == 1
        disabler.disabledRepositories.contains(repositoryId1)

        when:
        disabled = disabler.disableRepository(repositoryId2, exception)

        then:
        disabled
        disabler.disabledRepositories.size() == 2
        disabler.disabledRepositories.contains(repositoryId1)
        disabler.disabledRepositories.contains(repositoryId2)

        where:
        exception << [createTimeoutException(), createInternalServerException()]
    }

    def "does not disable repository for #type"() {
        when:
        boolean disabled = disabler.disableRepository('abc', exception)

        then:
        !disabled
        disabler.disabledRepositories.empty

        where:
        type                                        | exception
        'NullPointerException'                      | createNestedException(new NullPointerException())
        'HttpErrorStatusCodeException with status ' | createUnauthorizedException()
    }

    static RuntimeException createInternalServerException() {
        createHttpErrorStatusCodeException(500)
    }

    static RuntimeException createUnauthorizedException() {
        createHttpErrorStatusCodeException(401)
    }

    static RuntimeException createHttpErrorStatusCodeException(int statusCode) {
        createNestedException(new HttpErrorStatusCodeException('GET', 'test.file', statusCode, ''))
    }

    static RuntimeException createTimeoutException() {
        createNestedException(new InterruptedIOException('Read time out'))
    }

    static RuntimeException createNestedException(Throwable t) {
        new RuntimeException('Could not resolve module', t)
    }
}
