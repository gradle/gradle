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

package org.gradle.api.internal.changedetection.rules

import com.google.common.collect.ImmutableSortedMap
import spock.lang.Specification

class SortedMapDiffUtilTest extends Specification {

    def "property changes are detected"() {
        expect:
        diff(['a', 'b', 'c'], ['b', 'c', 'd']) == [added: ['d'], removed: ['a'], same: ['b', 'c']]
        diff(['a'], ['a']) == [added: [], removed: [], same: ['a']]
        diff(['a'], []) == [added: [], removed: ['a'], same: []]
        diff([], ['b']) == [added: ['b'], removed: [], same: []]
        diff(['a'], ['b']) == [added: ['b'], removed: ['a'], same: []]
        diff(['b'], ['a']) == [added: ['a'], removed: ['b'], same: []]
        diff(['a', 'b', 'd'], ['c', 'e', 'f', 'g']) == [added: ['c', 'e', 'f', 'g'], removed: ['a', 'b', 'd'], same: []]
        diff(['a', 'b', 'd', 'e'], ['c', 'e', 'f', 'g']) == [added: ['c', 'f', 'g'], removed: ['a', 'b', 'd'], same: ['e']]
        diff(['c', 'e', 'f', 'g'], ['a', 'b', 'd']) == [added: ['a', 'b', 'd'], removed: ['c', 'e', 'f', 'g'], same: []]
    }

    private static Map diff(List<String> previous, List<String> current) {
        SortedMap<String, String> previousMap = ImmutableSortedMap.copyOf(previous.collectEntries { [(it): 'previous' + it] })
        SortedMap<String, String> currentMap = ImmutableSortedMap.copyOf(current.collectEntries { [(it): 'current' + it] })

        Map result = [added: [], removed: [], same: []]
        SortedMapDiffUtil.diff(previousMap, currentMap, new PropertyDiffListener<String, String>() {
            @Override
            void removed(String previousProperty) {
                result.removed.add(previousProperty)
            }

            @Override
            void added(String currentProperty) {
                result.added.add(currentProperty)
            }

            @Override
            void maybeChanged(String property, String previousValue, String currentValue) {
                result.same.add(property)
            }
        })
        return result
    }

}
