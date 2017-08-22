/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.file.pattern

import spock.lang.Specification

class FixedPatternStepTest extends Specification {
    def "matches name case sensitive"() {
        def step = new FixedPatternStep("name", true)

        expect:
        step.matches("name")
        !step.matches("Name")
        !step.matches("")
        !step.matches("something else")
    }

    def "matches name case insensitive"() {
        def step = new FixedPatternStep("name", false)

        expect:
        step.matches("name")
        step.matches("Name")
        step.matches("NAME")
        !step.matches("")
        !step.matches("something else")
    }
}
