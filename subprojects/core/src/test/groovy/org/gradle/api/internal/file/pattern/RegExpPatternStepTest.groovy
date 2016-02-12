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
package org.gradle.api.internal.file.pattern

import spock.lang.Specification
import spock.lang.Unroll;


class RegExpPatternStepTest extends Specification {
    def "patterns are correctly converted"() {
        expect:
        'literal' == RegExpPatternStep.getRegExPattern('literal')
        'dotq.' == RegExpPatternStep.getRegExPattern('dotq?')
        'star.*stuff' == RegExpPatternStep.getRegExPattern('star*stuff')
        '"\\\\\\[\\]\\^\\-\\&\\.\\{\\}\\(\\)\\$\\+\\|\\<\\=\\!"' == RegExpPatternStep.getRegExPattern('"\\[]^-&.{}()$+|<=!"')
        '"\\$\\&time"' == RegExpPatternStep.getRegExPattern('"$&time"')
    }

    @Unroll
    def "special characters are correctly converted when isFile:#isFile"() {
        String testChars = '`~!@#$%^&*()-_=+[]{}\\|;:\'"<>,/'
        RegExpPatternStep step = new RegExpPatternStep(testChars, true)

        expect:
        step.matches(testChars, isFile)

        where:
        isFile << [true, false]
    }

    @Unroll
    def "literal matches work when isFile:#isFile"() {
        RegExpPatternStep step = new RegExpPatternStep('literal', true)

        expect:
        step.matches('literal', isFile)

        and:
        !step.matches('Literal', isFile)
        !step.matches('literally', isFile)
        !step.matches('aliteral', isFile)

        where:
        isFile << [true, false]
    }

    @Unroll
    def "single-character wildcard (?) works when isFile:#isFile"() {
        RegExpPatternStep step = new RegExpPatternStep('a?c', true)

        expect:
        step.matches('abc', isFile)
        step.matches('a$c', isFile)
        step.matches('a?c', isFile)

        and:
        !step.matches('ac', isFile)
        !step.matches('abcd', isFile)
        !step.matches('abd', isFile)
        !step.matches('a', isFile)

        where:
        isFile << [true, false]
    }

    @Unroll
    def "multipile-character wildcard (*) works when isFile:#isFile"() {
        given:
        RegExpPatternStep aSplatC = new RegExpPatternStep('a*c', true)
        and:
        RegExpPatternStep splat = new RegExpPatternStep('*', true)

        expect:
        aSplatC.matches('abc', isFile)
        aSplatC.matches('abrac', isFile)
        !aSplatC.matches('abcd', isFile)
        !aSplatC.matches('ab', isFile)
        !aSplatC.matches('a', isFile)

        and:
        splat.matches('asd;flkj', isFile)
        splat.matches('', isFile)

        where:
        isFile << [true, false]
    }

    @Unroll
    def "case sensitive #mixed matches #a but not #b when isFile:#isFile"() {
        given:
        RegExpPatternStep step = new RegExpPatternStep(mixed, true)

        expect:
        step.matches(a, isFile)
        !step.matches(b, isFile)

        where:
        mixed    | a        | b        | isFile
        'MiXeD'  | 'MiXeD'  | 'mixed'  | true
        'MiXeD?' | 'MiXeD1' | 'mixed1' | true
        'MiXeD'  | 'MiXeD'  | 'mixed'  | false
        'MiXeD?' | 'MiXeD1' | 'mixed1' | false
    }

    @Unroll
    def "case insensitive #mixed mathces #a and #b when isFile:#isFile"() {
        given:
        RegExpPatternStep step = new RegExpPatternStep(mixed, false)

        expect:
        step.matches(a, isFile)
        step.matches(b, isFile)

        where:
        mixed    | a        | b        | isFile
        'MiXeD'  | 'MiXeD'  | 'mixed'  | true
        'MiXeD?' | 'MiXeD1' | 'mixed1' | true
        'MiXeD'  | 'MiXeD'  | 'mixed'  | false
        'MiXeD?' | 'MiXeD1' | 'mixed1' | false
    }
}
