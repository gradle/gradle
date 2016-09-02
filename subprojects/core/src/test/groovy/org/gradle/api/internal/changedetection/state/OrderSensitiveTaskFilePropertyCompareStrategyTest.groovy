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
    OrderSensitiveTaskFilePropertyCompareStrategy strategy = new OrderSensitiveTaskFilePropertyCompareStrategy()

    def "detects addition"() {
        expect:
        changes(
            ["one-new": snapshot("one"), "two-new": snapshot("two")],
            ["one-old": snapshot("one")]
        ) == [change("two-new", ADDED)]
    }

    def "detects deletion"() {
        expect:
        changes(
            ["one-new": snapshot("one")],
            ["one-old": snapshot("one"), "two-old": snapshot("two")]
        ) == [change("two-old", REMOVED)]
    }

    def "detects modification"() {
        expect:
        changes(
            ["one-new": snapshot("one"), "two-new": snapshot("two", "9876fdcb")],
            ["one-old": snapshot("one"), "two-old": snapshot("two", "abcd1234")]
        ) == [change("two-new", MODIFIED)]
    }

    def "detects replacement"() {
        expect:
        changes(
            ["one-new": snapshot("one"), "two-new": snapshot("two"), "four-new": snapshot("four")],
            ["one-old": snapshot("one"), "three-old": snapshot("three"), "four-old": snapshot("four")]
        ) == [change("two-new", REPLACED)]
    }

    def "detects reordering"() {
        expect:
        changes(
            ["one-new": snapshot("one"), "two-new": snapshot("two"), "three-new": snapshot("three")],
            ["one-old": snapshot("one"), "three-old": snapshot("three"), "two-old": snapshot("two")]
        ) == [change("two-new", REPLACED), change("three-new", REPLACED)]
    }

    def "handles duplicates"() {
        expect:
        changes(
            ["one-new-1": snapshot("one"), "one-new-2": snapshot("one"), "two-new": snapshot("two")],
            ["one-old-1": snapshot("one"), "one-old-2": snapshot("one"), "two-old": snapshot("two")]
        ) == []
    }

    def changes(Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous) {
        return super.changes(strategy, current, previous)
    }
}
