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

package org.gradle.testing.internal.util

import spock.lang.Specification

class RetryUtilTest extends Specification {
    def "exceed retry count of #retryCount throws last exception"() {
        given:
        def closure = Mock(Closure)

        when:
        RetryUtil.retry(retryCount, closure)

        then:
        def exception = thrown(Exception)
        exception.message == "Exception for retry #${retryCount - 1}"
        interaction {
            retryCount.times { retryIndex ->
                1 * closure.call() >> {
                    throw new RuntimeException("Exception for retry #$retryIndex")
                }
            }
        }

        where:
        retryCount << [1, 2, 3, 10]
    }

    def "throwing Exception will count as an try"() {
        given:
        def closure = Mock(Closure)
        def retryCount = 1

        when:
        RetryUtil.retry(retryCount, closure)

        then:
        thrown(Exception)
        retryCount * closure.call() >> {
            throw new Exception()
        }
    }

    def "closure succeed on second try returns retry count of 2"() {
        given:
        def closure = Mock(Closure)
        def retryCount = 2

        when:
        def result = RetryUtil.retry(retryCount, closure)

        then:
        1 * closure.call() >> {
            throw new Exception()
        }
        1 * closure.call()
        noExceptionThrown()
        result == retryCount
    }
}
