/*
 * Copyright 2014 the original author or authors.
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

class HasSuffixPatternStepTest extends Specification {
    @Unroll
    def "matches name case sensitive when isFile is #isFile"() {
        def step = new HasSuffixPatternStep(".java", true)

        expect:
        step.matches("thing.java", isFile)
        step.matches(".java", isFile)
        !step.matches("thing.JAVA", isFile)
        !step.matches("thing.Java", isFile)
        !step.matches("thing.jav", isFile)
        !step.matches("thing.c", isFile)
        !step.matches("", isFile)
        !step.matches("something else", isFile)

        where:
        isFile << [true, false]
    }

    @Unroll
    def "matches name case insensitive when isFile is #isFile"() {
        def step = new HasSuffixPatternStep(".java", false)

        expect:
        step.matches("thing.java", isFile)
        step.matches(".java", isFile)
        step.matches("thing.JAVA", isFile)
        step.matches("thing.Java", isFile)
        !step.matches("thing.jav", isFile)
        !step.matches("thing.c", isFile)
        !step.matches("", isFile)
        !step.matches("something else", isFile)

        where:
        isFile << [true, false]
    }
}
