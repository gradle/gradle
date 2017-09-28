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
import spock.lang.Unroll

class ConnectionFailureRepositoryBlacklisterTest extends Specification {

    @Subject RepositoryBlacklister blacklister = new ConnectionFailureRepositoryBlacklister()

    def "initializes with no blacklisted repositories"() {
        expect:
        blacklister.blacklistedRepositories.empty
    }

    def "blacklists repository for normal IOException"() {
        given:
        def repositoryId1 = 'abc'
        def repositoryId2 = 'def'
        def exception = createIOException('Read time out')

        when:
        boolean blacklisted = blacklister.blacklistRepository(repositoryId1, exception)

        then:
        blacklisted
        blacklister.blacklistedRepositories.size() == 1
        blacklister.blacklistedRepositories.contains(repositoryId1)

        when:
        blacklisted = blacklister.blacklistRepository(repositoryId1, exception)

        then:
        blacklisted
        blacklister.blacklistedRepositories.size() == 1
        blacklister.blacklistedRepositories.contains(repositoryId1)

        when:
        blacklisted = blacklister.blacklistRepository(repositoryId2, exception)

        then:
        blacklisted
        blacklister.blacklistedRepositories.size() == 2
        blacklister.blacklistedRepositories.contains(repositoryId1)
        blacklister.blacklistedRepositories.contains(repositoryId2)
    }

    @Unroll
    def "does not blacklist repository for #type"() {
        when:
        boolean blacklisted = blacklister.blacklistRepository('abc', exception)

        then:
        !blacklisted
        blacklister.blacklistedRepositories.empty

        where:
        type                            | exception
        'NullPointerException'          | createNestedException(new NullPointerException())
        'HttpErrorStatusCodeExceptiion' | createHttpErrorStatusCodeException('HTTP 500')
    }

    static RuntimeException createHttpErrorStatusCodeException(String message) {
        createNestedException(new HttpErrorStatusCodeException(message))
    }

    static RuntimeException createIOException(String message) {
        createNestedException(new IOException(message))
    }

    static RuntimeException createNestedException(Throwable t) {
        new RuntimeException('Could not resolve module', t)
    }
}
