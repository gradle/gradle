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

package org.gradle.util

import org.gradle.util.internal.DefaultGradleVersion.Stage
import spock.lang.Specification

class StageTest extends Specification {
    def "builds simple stage"() {
        when:
        def s = Stage.from(3, "4")

        then:
        s.stage == 3
        s.number == 4
    }

    def "builds stage with patch no"() {
        when:
        def s = Stage.from(3, "4a")

        then:
        s.stage == 3
        s.number == 4
        s.patchNo == 'a'
    }

    def "compares correctly with patch no"() {
        expect:
        Stage.from(1, "2") < Stage.from(1, "3")
        Stage.from(1, "4") > Stage.from(1, "3")

        Stage.from(1, "9") < Stage.from(2, "1")
        Stage.from(2, "1") > Stage.from(1, "9")

        Stage.from(1, "1") < Stage.from(1, "1a")
        Stage.from(1, "1a") > Stage.from(1, "1")

        Stage.from(1, "1a") < Stage.from(1, "1b")
        Stage.from(1, "1b") > Stage.from(1, "1a")

        Stage.from(1, "1c") < Stage.from(1, "2b")
        Stage.from(1, "2b") > Stage.from(1, "1c")
    }

    def "shows input when matching fails"() {
        expect:
        Stage.from(1, "x") == null
    }
}
