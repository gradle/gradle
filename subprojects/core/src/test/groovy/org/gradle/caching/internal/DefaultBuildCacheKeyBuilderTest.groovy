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

package org.gradle.caching.internal

import spock.lang.Specification

class DefaultBuildCacheKeyBuilderTest extends Specification {

    def 'hash collision for bytes'() {
        def left = [[1, 2, 3], [4, 5]]
        def right = [[1, 2], [3, 4, 5]]
        expect:
        hashKey(left).hashCode != hashKey(right).hashCode
    }

    def 'hash collision for strings'() {
        expect:
        hashStrings(["abc", "de"]).hashCode != hashStrings(["ab", "cde"]).hashCode
    }

    def hashStrings(List<String> strings) {
        def builder = new DefaultBuildCacheKeyBuilder()
        strings.each { builder.putString(it) }
        builder.build()
    }

    def hashKey(List<List<Integer>> bytes) {
        def builder = new DefaultBuildCacheKeyBuilder()
        bytes.each {
            if (it.size() == 1) {
                builder.putByte(it[0] as byte)
            } else {
                builder.putBytes(it as byte[])
            }
        }
        builder.build()
    }
}
