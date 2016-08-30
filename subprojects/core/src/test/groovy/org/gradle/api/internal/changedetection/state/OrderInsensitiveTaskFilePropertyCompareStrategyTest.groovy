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

import spock.lang.Shared
import spock.lang.Unroll

import static org.gradle.api.internal.changedetection.rules.ChangeType.*

class OrderInsensitiveTaskFilePropertyCompareStrategyTest extends AbstractTaskFilePropertyCompareStrategyTest {
    @Shared static TaskFilePropertyCompareStrategy includeAdded = new OrderInsensitiveTaskFilePropertyCompareStrategy(true) {
        @Override
        String toString() {
            return "include added files"
        }
    }
    @Shared static TaskFilePropertyCompareStrategy excludeAdded = new OrderInsensitiveTaskFilePropertyCompareStrategy(false) {
        @Override
        String toString() {
            return "exclude added files"
        }
    }

    @Unroll
    def "detects addition (#strategy)"() {
        expect:
        changes(strategy,
            ["one-new": snapshot("one"), "two-new": snapshot("two")],
            ["one-old": snapshot("one")]
        ) == results
        where:
        strategy     | results
        includeAdded | [change("two-new", ADDED)]
        excludeAdded | []
    }

    @Unroll
    def "detects deletion (#strategy)"() {
        expect:
        changes(strategy,
            ["one-new": snapshot("one")],
            ["one-old": snapshot("one"), "two-old": snapshot("two")]
        ) == [change("two-old", REMOVED)]
        where:
        strategy << [includeAdded, excludeAdded]
    }

    @Unroll
    def "detects modification (#strategy)"() {
        expect:
        changes(strategy,
            ["one-new": snapshot("one"), "two-new": snapshot("two", "9876fdcb")],
            ["one-old": snapshot("one"), "two-old": snapshot("two", "abcd1234")]
        ) == [change("two-new", MODIFIED)]
        where:
        strategy << [includeAdded, excludeAdded]
    }

    @Unroll
    def "detects replacement as addition and removal (#strategy)"() {
        expect:
        changes(strategy,
            ["one-new": snapshot("one"), "two-new": snapshot("two"), "four-new": snapshot("four")],
            ["one-old": snapshot("one"), "three-old": snapshot("three"), "four-old": snapshot("four")]
        ) == results
        where:
        strategy     | results
        includeAdded | [change("three-old", REMOVED), change("two-new", ADDED)]
        excludeAdded | [change("three-old", REMOVED)]
    }

    @Unroll
    def "does not detect reordering (#strategy)"() {
        expect:
        changes(strategy,
            ["one-new": snapshot("one"), "two-new": snapshot("two"), "three-new": snapshot("three")],
            ["one-old": snapshot("one"), "three-old": snapshot("three"), "two-old": snapshot("two")]
        ) == []
        where:
        strategy << [includeAdded, excludeAdded]
    }
}
