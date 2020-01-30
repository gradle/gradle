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

package org.gradle.api.publish.internal

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.util.concurrent.Callable

@Subject(DefaultPublishRetrier)
class DefaultPublishRetrierTest extends Specification {

    def 'retries operation if transient network issue - #ex'() {
        when:
        int retried = 0
        Callable operation = {
            retried++
            throw ex
        }
        DefaultPublishRetrier defaultPublishRetrier = new DefaultPublishRetrier(operation, "my-repository")
        defaultPublishRetrier.publishWithRetry()

        then:
        retried == 3
        thrown(PublishRetrierException)

        where:
        ex << [
            new SocketTimeoutException("something went wrong"),
            new RuntimeException("with cause", new SocketTimeoutException("something went wrong"))
        ]
    }

    def 'does not retry operation if not transient network issue - #ex'() {
        when:
        int retried = 0
        Callable operation = {
            retried++
            throw ex
        }
        DefaultPublishRetrier defaultPublishRetrier = new DefaultPublishRetrier(operation, "my-repository")
        defaultPublishRetrier.publishWithRetry()

        then:
        retried == 1
        thrown(PublishRetrierException)

        where:
        ex << [
            new RuntimeException("non network issue")
        ]
    }

    @Unroll
    def "DefaultPublishRetrier is not created if max attempts or initial backoff is 0 or less"() {
        when:
        System.setProperty(systemProperty, value)
        Callable operation = {
            throw new RuntimeException("non network issue")
        }
        DefaultPublishRetrier defaultPublishRetrier = new DefaultPublishRetrier(operation, "my-repository")

        then:
        thrown(AssertionError)

        where:
        systemProperty | value
        "org.gradle.internal.remote.repository.deploy.max.attempts" | "-1"
        "org.gradle.internal.remote.repository.deploy.max.attempts" | "0"
        "org.gradle.internal.remote.repository.deploy.initial.backoff" | "-1"
        "org.gradle.internal.remote.repository.deploy.initial.backoff" | "0"
    }
}
