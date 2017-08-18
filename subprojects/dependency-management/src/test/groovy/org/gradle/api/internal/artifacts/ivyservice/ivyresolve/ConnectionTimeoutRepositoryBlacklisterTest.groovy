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
        blacklister.blacklistRepository(repositoryId1, createNestedSocketTimeoutException('Read time out'))

        then:
        blacklister.blacklistedRepositories.size() == 1
        blacklister.blacklistedRepositories.contains(repositoryId1)

        when:
        blacklister.blacklistRepository(repositoryId1, createNestedSocketTimeoutException('Some other issue later'))

        then:
        blacklister.blacklistedRepositories.size() == 1
        blacklister.blacklistedRepositories.contains(repositoryId1)

        when:
        blacklister.blacklistRepository(repositoryId2, createNestedSocketTimeoutException('More issues'))

        then:
        blacklister.blacklistedRepositories.size() == 2
        blacklister.blacklistedRepositories.contains(repositoryId1)
        blacklister.blacklistedRepositories.contains(repositoryId2)
    }

    static RuntimeException createNestedSocketTimeoutException(String message) {
        new RuntimeException('Could not resolve module', new SocketTimeoutException(message))
    }
}
