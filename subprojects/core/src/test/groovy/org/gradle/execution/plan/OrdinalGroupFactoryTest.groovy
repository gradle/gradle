/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.execution.plan

import spock.lang.Specification

class OrdinalGroupFactoryTest extends Specification {

    def 'groups can be accessed in any order'() {
        given:
        def subject = new OrdinalGroupFactory()

        when:
        def g2 = subject.group(2)
        def g0 = subject.group(0)
        def g1 = subject.group(1)

        then:
        subject.allGroups == [g0, g1, g2]
    }

    def 'returns the same group for the same ordinal'() {
        given:
        def subject = new OrdinalGroupFactory()

        when:
        def g0 = subject.group(0)
        def g1 = subject.group(1)

        then:
        g1 != g0
        subject.group(0) == g0
        subject.group(1) == g1

        and:
        g0.ordinal == 0
        g1.ordinal == 1
    }
}
