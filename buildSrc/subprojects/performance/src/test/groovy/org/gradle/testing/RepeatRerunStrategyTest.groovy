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
import spock.lang.Unroll

class RepeatRerunStrategyTest extends Specification {

    @Unroll
    def "reruns the scenario the given number of times (outcome: #outcome)"() {
        def rerunStrategy = new RepeatRerunStrategy(3)
        expect:
        rerunStrategy.shouldRerun(1, outcome)
        rerunStrategy.shouldRerun(2, outcome)

        !rerunStrategy.shouldRerun(3, outcome)
        !rerunStrategy.shouldRerun(4, outcome)

        where:
        outcome << [true, false]
    }
}
