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

import spock.lang.Specification
import spock.lang.Subject

class ConnectionTimeoutRepositoryBlacklisterTest extends Specification {

    @Subject RepositoryBlacklister blacklister = new ConnectionInterruptionRepositoryBlacklister()

    def "initializes with no blacklisted repositories"() {
        expect:
        blacklister.blacklistedRepositories.empty
    }

    def "blacklists repository for InterruptedIOException"() {
        given:
        def repositoryId1 = 'abc'
        def repositoryId2 = 'def'

        when:
        boolean blacklisted = blacklister.blacklistRepository(repositoryId1, createNestedSocketTimeoutException('Read time out'))

        then:
        blacklisted
        blacklister.blacklistedRepositories.size() == 1
        blacklister.blacklistedRepositories.contains(repositoryId1)

        when:
        blacklisted = blacklister.blacklistRepository(repositoryId1, createNestedSocketTimeoutException('Some other issue later'))

        then:
        blacklisted
        blacklister.blacklistedRepositories.size() == 1
        blacklister.blacklistedRepositories.contains(repositoryId1)

        when:
        blacklisted = blacklister.blacklistRepository(repositoryId2, createNestedSocketTimeoutException('More issues'))

        then:
        blacklisted
        blacklister.blacklistedRepositories.size() == 2
        blacklister.blacklistedRepositories.contains(repositoryId1)
        blacklister.blacklistedRepositories.contains(repositoryId2)
    }

    def "does not blacklist repository for other exception"() {
        when:
        boolean blacklisted = blacklister.blacklistRepository('abc', createNestedException(new NullPointerException()))

        then:
        !blacklisted
        blacklister.blacklistedRepositories.empty
    }

    static RuntimeException createNestedSocketTimeoutException(String message) {
        createNestedException(new SocketTimeoutException(message))
    }

    static RuntimeException createNestedException(Throwable t) {
        new RuntimeException('Could not resolve module', t)
    }
}
