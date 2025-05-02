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

package org.gradle.buildinit.plugins.internal.modifiers

import spock.lang.Specification


class NamesTest extends Specification {
    enum Test {
        ONE,
        THING_TWO
    }

    def "calculates display name"() {
        expect:
        Names.displayNameFor(Test.ONE) == "one"
        Names.displayNameFor(Test.THING_TWO) == "thing two"
    }

    def "calculates id"() {
        expect:
        Names.idFor(Test.ONE) == "one"
        Names.idFor(Test.THING_TWO) == "thing-two"
    }
}
