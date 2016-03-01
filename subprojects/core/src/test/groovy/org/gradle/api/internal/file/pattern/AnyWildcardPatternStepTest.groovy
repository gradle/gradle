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
package org.gradle.api.internal.file.pattern

import spock.lang.Specification
import spock.lang.Unroll

class AnyWildcardPatternStepTest extends Specification {
    @Unroll
    def "when matchesDirs:#matchesDirs it #matches when isFile:#isFile"() {
        def step = new AnyWildcardPatternStep(matchesDirs)

        expect:
        step.matches("whatever", isFile) == aMatch

        where:
        matchesDirs | isFile || aMatch | matches
        true        | true   || true   | 'matches'
        true        | false  || true   | 'matches'
        true        | true   || true   | 'matches'
        false       | false  || false  | 'doesn\'t match'
    }
}
