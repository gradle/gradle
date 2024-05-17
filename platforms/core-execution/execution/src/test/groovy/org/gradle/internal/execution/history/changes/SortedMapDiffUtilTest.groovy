/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.execution.history.changes

import com.google.common.collect.ImmutableSortedMap
import spock.lang.Specification

class SortedMapDiffUtilTest extends Specification {

    def "diff #previous and #current"() {
        expect:
        diff(previous, current) == [removed: removed, updated: updated, added: added]

        where:
        previous             | current              | removed              | updated    | added
        ['a', 'b', 'c']      | ['b', 'c', 'd']      | ['a']                | ['b', 'c'] | ['d']
        ['a']                | ['a']                | []                   | ['a']      | []
        ['a']                | []                   | ['a']                | []         | []
        []                   | ['b']                | []                   | []         | ['b']
        ['b']                | ['a']                | ['b']                | []         | ['a']
        ['a']                | ['b']                | ['a']                | []         | ['b']
        ['a', 'b', 'd']      | ['c', 'e', 'f', 'g'] | ['a', 'b', 'd']      | []         | ['c', 'e', 'f', 'g']
        ['a', 'b', 'd', 'e'] | ['c', 'e', 'f', 'g'] | ['a', 'b', 'd']      | ['e']      | ['c', 'f', 'g']
        ['c', 'e', 'f', 'g'] | ['a', 'b', 'd']      | ['c', 'e', 'f', 'g'] | []         | ['a', 'b', 'd']

    }

    private static Map diff(List<String> previous, List<String> current) {
        SortedMap<String, String> previousMap = ImmutableSortedMap.copyOf(previous.collectEntries { [(it): 'previous' + it] })
        SortedMap<String, String> currentMap = ImmutableSortedMap.copyOf(current.collectEntries { [(it): 'current' + it] })

        Map result = [added: [], removed: [], updated: []]
        SortedMapDiffUtil.diff(previousMap, currentMap, new PropertyDiffListener<String, String, String>() {
            @Override
            boolean removed(String previousProperty) {
                result.removed.add(previousProperty)
                return true
            }

            @Override
            boolean added(String currentProperty) {
                result.added.add(currentProperty)
                return true
            }

            @Override
            boolean updated(String property, String previousValue, String currentValue) {
                result.updated.add(property)
                return true
            }
        })
        return result
    }

}
