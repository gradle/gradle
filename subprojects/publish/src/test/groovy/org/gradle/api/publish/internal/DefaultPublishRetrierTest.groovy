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

import org.gradle.api.publish.PublishRetrierException
import spock.lang.Specification
import spock.lang.Subject

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
        DefaultPublishRetrier defaultPublishRetrier = new DefaultPublishRetrier(operation, "my-repository", 3, 1)
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
        DefaultPublishRetrier defaultPublishRetrier = new DefaultPublishRetrier(operation, "my-repository", 3, 1)
        defaultPublishRetrier.publishWithRetry()

        then:
        retried == 1
        thrown(PublishRetrierException)

        where:
        ex << [
            new RuntimeException("non network issue")
        ]
    }
}
