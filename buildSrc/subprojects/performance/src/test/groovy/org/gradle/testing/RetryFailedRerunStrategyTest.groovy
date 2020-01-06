/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.testing

import spock.lang.Specification

class RetryFailedRerunStrategyTest extends Specification {

    def "doesn't retry when test didn't fail"() {
        def retryFailedRerunStrategy = new RetryFailedRerunStrategy(1)
        expect:
        !retryFailedRerunStrategy.shouldRerun(1, true)
        !retryFailedRerunStrategy.shouldRerun(3, true)
    }

    def "retries the test retry count times"() {
        def retryFailedRerunStrategy = new RetryFailedRerunStrategy(2)
        expect:
        retryFailedRerunStrategy.shouldRerun(1, false)
        retryFailedRerunStrategy.shouldRerun(2, false)

        !retryFailedRerunStrategy.shouldRerun(3, false)
    }

    def "retries only up to max failing test times"() {
        def retryFailedRerunStrategy = new RetryFailedRerunStrategy(2)
        expect:
        15.times {
            assert retryFailedRerunStrategy.shouldRerun(1, false)

        }
        !retryFailedRerunStrategy.shouldRerun(1, false)
    }
}
