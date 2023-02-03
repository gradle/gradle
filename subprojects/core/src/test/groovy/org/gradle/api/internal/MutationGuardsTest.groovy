/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal

import spock.lang.Specification
import spock.lang.Subject

@Subject(MutationGuards)
class MutationGuardsTest extends Specification {
    def "can get mutation guard of guard aware instance"() {
        given:
        def subject = Mock(WithMutationGuard)

        when:
        MutationGuards.of(subject)

        then:
        1 * subject.getMutationGuard()
        0 * subject._
    }

    def "doesn't get mutation guard of guard unaware instance"() {
        given:
        def subject = Mock(WithoutMutationGuard)

        when:
        MutationGuards.of(subject)

        then:
        0 * subject.getMutationGuard()
        0 * subject._
    }

    interface WithoutMutationGuard {
        MutationGuard getMutationGuard()
    }
}
