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

import org.gradle.util.GradleVersion.Stage
import spock.lang.Specification

class StageTest extends Specification {
    def "builds simple stage"() {
        when:
        def s = new Stage(3, "4")

        then:
        s.stage == 3
        s.number == 4
    }

    def "builds stage with patch no"() {
        when:
        def s = new Stage(3, "4a")

        then:
        s.stage == 3
        s.number == 4
        s.patchNo == 'a'
    }

    def "compares correctly with patch no"() {
        expect:
        new Stage(1, "2") < new Stage(1, "3")
        new Stage(1, "4") > new Stage(1, "3")

        new Stage(1, "9") < new Stage(2, "1")
        new Stage(2, "1") > new Stage(1, "9")

        new Stage(1, "1") < new Stage(1, "1a")
        new Stage(1, "1a") > new Stage(1, "1")

        new Stage(1, "1a") < new Stage(1, "1b")
        new Stage(1, "1b") > new Stage(1, "1a")

        new Stage(1, "1c") < new Stage(1, "2b")
        new Stage(1, "2b") > new Stage(1, "1c")
    }

    def "shows input when matching fails"() {
        when:
        new Stage(1, "x")

        then:
        def ex = thrown(Exception)
        ex.message.contains("x")
    }
}
