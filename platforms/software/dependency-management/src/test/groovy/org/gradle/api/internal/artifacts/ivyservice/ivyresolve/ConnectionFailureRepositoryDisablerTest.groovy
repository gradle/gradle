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

import org.apache.http.NoHttpResponseException
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParseException
import org.gradle.internal.resource.HttpErrorStatusCodeException
import spock.lang.Specification
import spock.lang.Subject

import javax.net.ssl.SSLHandshakeException

class ConnectionFailureRepositoryDisablerTest extends Specification {

    @Subject RepositoryDisabler disabler = new ConnectionFailureRepositoryDisabler()

    def "disables repository for critical exception [#exception]"() {
        given:
        def repositoryId1 = 'abc'
        def repositoryId2 = 'def'

        when:
        boolean disabled = disabler.tryDisableRepository(repositoryId1, exception, false)

        then:
        disabled
        disabler.disabledRepositories.size() == 1
        disabler.disabledRepositories.contains(repositoryId1)

        when:
        disabled = disabler.tryDisableRepository(repositoryId1, exception, false)

        then:
        disabled
        disabler.disabledRepositories.size() == 1
        disabler.disabledRepositories.contains(repositoryId1)

        when:
        disabled = disabler.tryDisableRepository(repositoryId2, exception, false)

        then:
        disabled
        disabler.disabledRepositories.size() == 2
        disabler.disabledRepositories.contains(repositoryId1)
        disabler.disabledRepositories.contains(repositoryId2)

        where:
        exception << [
            createTimeoutException(),
            createInternalServerException(),
            createHttpErrorStatusCodeException(408),
            createHttpErrorStatusCodeException(429),
            // An unrecognised failure cannot be attributed to the request, so it counts against the repository
            createNestedException(new NullPointerException()),
            // A handshake that cannot be completed is about the repository, not about this request
            createNestedException(new SSLHandshakeException('Received fatal alert: handshake_failure'))
        ]
    }

    def "does not disable repository when the failure is about the request [#type]"() {
        when:
        boolean disabled = disabler.tryDisableRepository('abc', exception, false)

        then:
        !disabled
        disabler.disabledRepositories.empty

        where:
        type                                 | exception
        'unauthorized'                       | createUnauthorizedException()
        'forbidden'                          | createHttpErrorStatusCodeException(403)
        'bad request'                        | createHttpErrorStatusCodeException(400)
        'gone'                               | createHttpErrorStatusCodeException(410)
        'unparseable metadata'               | createNestedException(new MetaDataParseException('Could not parse POM the-pom'))
        'a status from a non-HTTP transport' | createNestedException(new HttpErrorStatusCodeException(URI.create('s3://bucket/test.file'), "Could not get resource 's3://bucket/test.file'.", 403, new RuntimeException('AccessDenied')))
        'metadata parser failure'            | createNestedException(parseFailureWrappingParserError())
    }

    def "disables repository when max retries reached for transient error"() {
        when:
        boolean disabled = disabler.tryDisableRepository('abc', new NoHttpResponseException("No response from server"), true)

        then:
        disabled
        disabler.disabledRepositories.size() == 1
        disabler.disabledRepositories.contains('abc')
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

    static MetaDataParseException parseFailureWrappingParserError() {
        def failure = new MetaDataParseException('Could not parse POM the-pom')
        failure.initCause(new RuntimeException('XML document structures must start and end within the same entity.'))
        failure
    }

    static RuntimeException createTimeoutException() {
        createNestedException(new InterruptedIOException('Read time out'))
    }

    static RuntimeException createNestedException(Throwable t) {
        new RuntimeException('Could not resolve module', t)
    }
}
