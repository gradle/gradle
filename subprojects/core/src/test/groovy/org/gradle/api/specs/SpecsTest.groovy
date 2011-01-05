/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.specs

import spock.lang.Specification

/**
 * @author Hans Dockter
 */
class SpecsTest extends Specification {
    def filterIterable() {
        List list = ['a', 'b', 'c']

        expect:
        ['a', 'c'] as Set == Specs.filterIterable(list, Specs.convertClosureToSpec{ item -> item != 'b' })
    }

    def filterIterableWithNullReturningSpec() {
        expect:
        [] as Set == Specs.filterIterable(['a'], Specs.convertClosureToSpec { item -> println item })
    }
}
