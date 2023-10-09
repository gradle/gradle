/*
 * Copyright 2023 the original author or authors.
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

class RenderingUtilsTest extends Specification {
    def "oxford comma"() {
        when:
        def render = RenderingUtils.oxfordListOf(items, "and")

        then:
        render == result

        where:
        items               | result
        ["i1", "i2", "i3"] | "'i1', 'i2', and 'i3'"
        ["i1", "i2"]       | "'i1' and 'i2'"
        ["i1"]             | "'i1'"
        []                 | ""
    }
}
