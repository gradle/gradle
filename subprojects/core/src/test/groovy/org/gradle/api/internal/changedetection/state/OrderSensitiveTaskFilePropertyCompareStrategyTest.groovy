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

package org.gradle.api.internal.changedetection.state

import static org.gradle.api.internal.changedetection.rules.ChangeType.*

class OrderSensitiveTaskFilePropertyCompareStrategyTest extends AbstractTaskFilePropertyCompareStrategyTest {
    TaskFilePropertyCompareStrategy strategy = new OrderSensitiveTaskFilePropertyCompareStrategy()

    def "detects addition"() {
        expect:
        changes([snapshot("one"), snapshot("two")], [snapshot("one")]) == [change("two", ADDED)]
    }

    def "detects deletion"() {
        expect:
        changes([snapshot("one")], [snapshot("one"), snapshot("two")]) == [change("two", REMOVED)]
    }

    def "detects modification"() {
        expect:
        changes([snapshot("one"), snapshot("two", false)], [snapshot("one"), snapshot("two")]) == [change("two", MODIFIED)]
    }

    def "detects replacement"() {
        expect:
        changes([snapshot("one"), snapshot("two"), snapshot("four")], [snapshot("one"), snapshot("three"), snapshot("four")]) == [change("two", REPLACED)]
    }

    def "detects reordering"() {
        expect:
        changes([snapshot("one"), snapshot("two"), snapshot("three")], [snapshot("one"), snapshot("three"), snapshot("two")]) == [change("two", REPLACED), change("three", REPLACED)]
    }

    def changes(Collection<NormalizedFileSnapshot> current, Collection<NormalizedFileSnapshot> previous) {
        return super.changes(strategy, current, previous)
    }
}
