/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.problems.internal

import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import spock.lang.Specification

class DefaultProblemIdTest extends Specification {

    def "string representation is correct"() {
        given:
        ProblemGroup g1 = new DefaultProblemGroup("g-1", "d/g-1")
        ProblemGroup g2 = new DefaultProblemGroup("g-2", "d/g-2", g1)
        ProblemId id = new DefaultProblemId("id", "d/id", g2)

        expect:
        id.toString() == "g-1:g-2:id"
    }

}
