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

package org.gradle.internal.time

import spock.lang.Specification

class SwappableTimeSourceTest extends Specification {

    def initial = new MockTimeSource(nanos: 1, millis: 10)
    def replacement = new MockTimeSource(nanos: 2, millis: 20)
    def source = new SwappableTimeSource(initial)

    def "reads from the initial delegate"() {
        expect:
        source.get().is(initial)
        source.nanoTime() == 1
        source.currentTimeMillis() == 10
    }

    def "reads from the replacement once set"() {
        when:
        source.set(replacement)

        then:
        source.get().is(replacement)
        source.nanoTime() == 2
        source.currentTimeMillis() == 20
    }

    def "can be set back to a previous delegate"() {
        given:
        source.set(replacement)

        when:
        source.set(initial)

        then:
        source.get().is(initial)
        source.nanoTime() == 1
    }

    private static class MockTimeSource implements TimeSource {

        long nanos
        long millis

        @Override
        long currentTimeMillis() {
            return millis
        }

        @Override
        long nanoTime() {
            return nanos
        }

    }

}
