/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests.tooling.fixture

import org.gradle.util.GradleVersion
import spock.lang.Specification

class GradleVersionPredicateTest extends Specification {
    def "greater-than-or-equal version constraint matches all versions with specified base version and later"() {
        def predicate = new GradleVersionPredicate().toPredicate(">=1.0")

        expect:
        predicate.test(GradleVersion.version("1.0"))
        predicate.test(GradleVersion.version("1.0-milestone-9"))
        predicate.test(GradleVersion.version("1.0-snapshot-1"))
        predicate.test(GradleVersion.version("1.0-rc-1"))
        predicate.test(GradleVersion.version("1.0-12341010120000+1000"))
        predicate.test(GradleVersion.version("1.1"))
        predicate.test(GradleVersion.version("1.1-rc-7"))
        predicate.test(GradleVersion.version("1.1-12341010120000+1000"))
        predicate.test(GradleVersion.version("2.56"))

        !predicate.test(GradleVersion.version("0.9.2"))
        !predicate.test(GradleVersion.version("0.5"))
    }

    def "greater-than version constraint matches all versions later than specified base version"() {
        def predicate = new GradleVersionPredicate().toPredicate(">1.0")

        expect:
        !predicate.test(GradleVersion.version("1.0"))
        !predicate.test(GradleVersion.version("1.0-milestone-9"))
        !predicate.test(GradleVersion.version("1.0-snapshot-1"))
        !predicate.test(GradleVersion.version("1.0-rc-1"))
        !predicate.test(GradleVersion.version("1.0-12341010120000+1000"))

        predicate.test(GradleVersion.version("1.1"))
        predicate.test(GradleVersion.version("1.1-rc-7"))
        predicate.test(GradleVersion.version("1.1-12341010120000+1000"))
        predicate.test(GradleVersion.version("2.56"))

        !predicate.test(GradleVersion.version("0.9.2"))
        !predicate.test(GradleVersion.version("0.5"))
    }

    def "less-than-or-equal version constraint matches all versions with specified base version and earlier"() {
        def predicate = new GradleVersionPredicate().toPredicate("<=1.4")

        expect:
        predicate.test(GradleVersion.version("1.4"))
        predicate.test(GradleVersion.version("1.4-rc-1"))
        predicate.test(GradleVersion.version("1.4-12341010120000+1000"))
        predicate.test(GradleVersion.version("1.3"))
        predicate.test(GradleVersion.version("1.3-rc-7"))
        predicate.test(GradleVersion.version("1.3-12341010120000+1000"))
        predicate.test(GradleVersion.version("1.0-milestone-9"))
        predicate.test(GradleVersion.version("0.9.2"))

        !predicate.test(GradleVersion.version("1.5"))
        !predicate.test(GradleVersion.version("1.5-rc-1"))
        !predicate.test(GradleVersion.version("12.45"))
    }

    def "less-than version constraint matches versions earlier than specified version"() {
        def predicate = new GradleVersionPredicate().toPredicate("<1.4")

        expect:
        predicate.test(GradleVersion.version("1.3"))
        predicate.test(GradleVersion.version("1.3-rc-7"))
        predicate.test(GradleVersion.version("1.3-12341010120000+1000"))
        predicate.test(GradleVersion.version("1.0-milestone-9"))
        predicate.test(GradleVersion.version("0.9.2"))

        !predicate.test(GradleVersion.version("1.4"))
        !predicate.test(GradleVersion.version("1.4-rc-1"))
        !predicate.test(GradleVersion.version("1.4-12341010120000+1000"))
        !predicate.test(GradleVersion.version("1.5"))
        !predicate.test(GradleVersion.version("1.5-rc-1"))
        !predicate.test(GradleVersion.version("12.45"))
    }

    def "equals version constraint matches versions with same base version"() {
        def predicate = new GradleVersionPredicate().toPredicate("=1.4")

        expect:
        predicate.test(GradleVersion.version("1.4"))
        predicate.test(GradleVersion.version("1.4-rc-7"))
        predicate.test(GradleVersion.version("1.4-12341010120000+1000"))

        !predicate.test(GradleVersion.version("1.0-milestone-9"))
        !predicate.test(GradleVersion.version("0.9.2"))
        !predicate.test(GradleVersion.version("1.5"))
        !predicate.test(GradleVersion.version("1.5-rc-1"))
        !predicate.test(GradleVersion.version("1.5-12341010120000+1000"))
        !predicate.test(GradleVersion.version("12.45"))
    }

    def "current version constraint matches current version"() {
        def predicate = new GradleVersionPredicate().toPredicate("current")

        expect:
        predicate.test(GradleVersion.current())

        !predicate.test(GradleVersion.version("1.0-milestone-9"))
        !predicate.test(GradleVersion.version("1.4-rc-7"))
        !predicate.test(GradleVersion.version("1.4-12341010120000+1000"))
        !predicate.test(GradleVersion.version("0.9.2"))
        !predicate.test(GradleVersion.version("1.5"))
        !predicate.test(GradleVersion.version("1.5-rc-1"))
        !predicate.test(GradleVersion.version("1.5-12341010120000+1000"))
        !predicate.test(GradleVersion.version("12.45"))
    }

    def "not current version constraint matches everything other than current version"() {
        def predicate = new GradleVersionPredicate().toPredicate("!current")

        expect:
        !predicate.test(GradleVersion.current())

        predicate.test(GradleVersion.version("1.0-milestone-9"))
        predicate.test(GradleVersion.version("1.4-rc-7"))
        predicate.test(GradleVersion.version("1.4-12341010120000+1000"))
        predicate.test(GradleVersion.version("0.9.2"))
        predicate.test(GradleVersion.version("1.5"))
        predicate.test(GradleVersion.version("1.5-rc-1"))
        predicate.test(GradleVersion.version("1.5-12341010120000+1000"))
        predicate.test(GradleVersion.version("12.45"))
    }

    def "range constraint matches all versions inside range"() {
        def predicate = new GradleVersionPredicate().toPredicate(">=1.0 <=1.4")

        expect:
        predicate.test(GradleVersion.version("1.0"))
        predicate.test(GradleVersion.version("1.0-milestone-9"))
        predicate.test(GradleVersion.version("1.0-rc-1"))
        predicate.test(GradleVersion.version("1.0-12341010120000+1000"))
        predicate.test(GradleVersion.version("1.1"))
        predicate.test(GradleVersion.version("1.1-rc-1"))
        predicate.test(GradleVersion.version("1.1-12341010120000+1000"))
        predicate.test(GradleVersion.version("1.4"))
        predicate.test(GradleVersion.version("1.4-rc-1"))
        predicate.test(GradleVersion.version("1.4-12341010120000+1000"))

        !predicate.test(GradleVersion.version("0.9.2"))
        !predicate.test(GradleVersion.version("0.5"))

        !predicate.test(GradleVersion.version("1.5"))
        !predicate.test(GradleVersion.version("1.5-rc-1"))
        !predicate.test(GradleVersion.version("12.45"))
    }

    def "can exclude versions"() {
        def predicate = new GradleVersionPredicate().toPredicate("!1.1 !1.3")

        expect:
        predicate.test(GradleVersion.version("1.0"))
        predicate.test(GradleVersion.version("1.0-milestone-9"))

        !predicate.test(GradleVersion.version("1.1"))
        !predicate.test(GradleVersion.version("1.1-rc-1"))
        !predicate.test(GradleVersion.version("1.1-12341010120000+1000"))

        !predicate.test(GradleVersion.version("1.3"))

        predicate.test(GradleVersion.version("1.4"))
        predicate.test(GradleVersion.version("1.4-12341010120000+1000"))
    }

    def "unsupported operator"() {
        when:
        new GradleVersionPredicate().toPredicate("-1.3")

        then:
        thrown(RuntimeException)
    }

    def "RuntimeExcpetion is thrown if a version lower than the minimal tested is specified"() {
        given:
        def predicate = new GradleVersionPredicate("3.0")

        when:
        predicate.toPredicate("<1.3")

        then:
        thrown(RuntimeException)
    }

    def "no RuntimeExcpetion is thrown if a version higher than the minimal tested is specified"() {
        given:
        def versionSpec = new GradleVersionPredicate("3.0")

        when:
        def predicate = versionSpec.toPredicate(">=3.2")

        then:
        predicate.test(GradleVersion.version("3.3"))
    }
}
