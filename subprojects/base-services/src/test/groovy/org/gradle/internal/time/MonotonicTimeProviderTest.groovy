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

package org.gradle.internal.time

import spock.lang.Specification

class MonotonicTimeProviderTest extends Specification {

    static class Time implements TimeProvider {
        long currentTime
    }

    def "prevents time from going backwards"() {
        when:
        def delegate = new Time()
        def provider = new MonotonicTimeProvider(delegate)

        then:
        provider.currentTime == 0

        when:
        delegate.currentTime = 10

        then:
        provider.currentTime == 10

        when:
        delegate.currentTime = 8

        then:
        provider.currentTime == 10

        when:
        delegate.currentTime = 10

        then:
        provider.currentTime == 10

        when:
        delegate.currentTime = 15

        then:
        provider.currentTime == 15
    }

}
