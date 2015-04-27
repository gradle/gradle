/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal

import spock.lang.Specification

class BiActionsTest extends Specification {

    def "composite action executes all actions that are part of it"() {
        given:
        BiAction<List<String>, List<String>> first = {a, b -> a << "first a"; b << "first b" }
        BiAction<List<String>, List<String>> second = {a, b -> a << "second a"; b << "second b" }
        def composite = BiActions.composite(first, second)
        def a = []
        def b = []

        when:
        composite.execute(a, b)

        then:
        a == ["first a", "second a"]
        b == ["first b", "second b"]
    }
}
