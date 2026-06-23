/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.internal.builders.dsl

import spock.lang.Specification

class ClosureConfigureTest extends Specification {
    static class Target {
        String value

        void known(String s) { value = s }
    }

    def "delegate-only strategy rejects typoed DSL calls"() {
        given:
        def target = new Target()

        when:
        ClosureConfigure.configure(target) {
            knwon("hi") // typo
        }

        then:
        thrown(MissingMethodException)
    }

    def "delegate-only strategy accepts valid DSL calls"() {
        given:
        def target = new Target()

        when:
        ClosureConfigure.configure(target) {
            known("hi")
        }

        then:
        target.value == "hi"
    }

    def "returns the configured target"() {
        given:
        def target = new Target()

        when:
        def result = ClosureConfigure.configure(target) {
            known("x")
        }

        then:
        result.is(target)
    }
}
