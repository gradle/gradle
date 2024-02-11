/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.util.internal

import spock.lang.Specification

class LimitedDescriptionTest extends Specification {

    def desc = new LimitedDescription(2)

    def "has limited description"() {
        when:
        desc.append("0").append("one").append("2").append("three !")

        then:
        desc.toString() == """2
three !
"""
    }

    def "is described even when empty"() {
        expect:
        desc.toString().length() != 0
    }
}
