/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.launcher.daemon.server.health.gc

import spock.lang.Specification


class DefaultSlidingWindowTest extends Specification {
    DefaultSlidingWindow<Integer> window = new DefaultSlidingWindow<Integer>(5)

    def "inserts objects up to the window limit and maintains constant size"() {
        when:
        (1..5).each { next ->
            window.slideAndInsert(next)
        }

        then:
        window.snapshot().size() == 5

        when:
        (6..8).each { next ->
            window.slideAndInsert(next)
        }

        then:
        window.snapshot().size() == 5

        and:
        window.snapshot() == [4,5,6,7,8] as Set
    }
}
