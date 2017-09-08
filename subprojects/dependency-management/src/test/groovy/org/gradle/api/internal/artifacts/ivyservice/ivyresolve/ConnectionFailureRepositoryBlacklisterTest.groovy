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

import org.gradle.api.UncheckedIOException
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ConnectionFailureRepositoryBlacklisterTest extends Specification {

    @Subject RepositoryBlacklister blacklister = new ConnectionFailureRepositoryBlacklister()

    def "initializes with no blacklisted repositories"() {
        expect:
        blacklister.blacklistedRepositories.empty
    }

    @Unroll
    def "blacklists repository for #type"() {
        given:
        def repositoryId1 = 'abc'
        def repositoryId2 = 'def'

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

        where:
        type                     | exception
        'InterruptedIOException' | createNestedSocketTimeoutException('Read time out')
        'UncheckedIOException'   | createNestedUncheckedIOException('Received status code 500 from server: broken')
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

    static RuntimeException createNestedUncheckedIOException(String message) {
        createNestedException(new UncheckedIOException(message))
    }

    static RuntimeException createNestedException(Throwable t) {
        new RuntimeException('Could not resolve module', t)
    }
}
