/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.util

import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.api.Transformer

import spock.lang.*

class CollectionUtilsTest extends Specification {

    def "list filtering"() {
        given:
        def spec = Specs.convertClosureToSpec { it < 5 }
        def filter = { Integer[] nums -> CollectionUtils.filter(nums as List, spec) }
        
        expect:
        filter(1,2,3) == [1,2,3]
        filter(7,8,9) == []
        filter() == []
        filter(4,5,6) == [4]
    }
    
    def "list collecting"() {
        def transformer = new Transformer() { def transform(i) { i * 2 } }
        def collect = { Integer[] nums -> CollectionUtils.collect(nums as List, transformer) }
        
        expect:
        collect(1,2,3) == [2,4,6]
        collect() == []
    }

    def "set filtering"() {
        given:
        def spec = Specs.convertClosureToSpec { it < 5 }
        def filter = { Integer[] nums -> CollectionUtils.filter(nums as Set, spec) }
        
        expect:
        filter(1,2,3) == [1,2,3] as Set
        filter(7,8,9).empty
        filter().empty
        filter(4,5,6) == [4] as Set
    }
    
}