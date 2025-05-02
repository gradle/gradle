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

package org.gradle.model.internal.inspect

import org.gradle.model.RuleSource
import org.gradle.test.fixtures.ConcurrentTestUtil
import spock.lang.Specification

class ModelRuleSourceDetectorTest extends Specification {

    private ModelRuleSourceDetector detector = new ModelRuleSourceDetector()

    static class HasOneSource {
        static class Source extends RuleSource {}

        static class NotSource {}
    }

    static class HasTwoSources {
        static class SourceOne extends RuleSource {}

        static class SourceTwo extends RuleSource {}

        static class NotSource {}
    }

    static class IsASource extends RuleSource {
    }

    static class SourcesNotDeclaredAlphabetically {
        static class B extends RuleSource {}

        static class A extends RuleSource {}
    }

    def "find model rule sources - #clazz"() {
        expect:
        detector.getDeclaredSources(clazz).toList() == expected

        where:
        clazz         | expected
        String        | []
        HasOneSource  | [HasOneSource.Source]
        HasTwoSources | [HasTwoSources.SourceOne, HasTwoSources.SourceTwo]
        IsASource     | [IsASource]
    }

    def "has model sources - #clazz"() {
        expect:
        detector.hasRules(clazz) == expected

        where:
        clazz        | expected
        String       | false
        HasOneSource | true
        IsASource    | true
    }

    def "does not hold strong reference"() {
        given:
        def cl = new GroovyClassLoader(getClass().classLoader)
        addClass(cl, impl)

        expect:
        detector.cache.size() == 1

        when:
        cl.clearCache()

        then:
        ConcurrentTestUtil.poll(10) {
            System.gc()
            detector.cache.cleanUp()
            detector.cache.size() == 0
        }

        where:
        impl << [
                "class SomeThing {}",
                "class SomeThing extends ${RuleSource.name} {}",
                "class SomeThing { static class Inner extends ${RuleSource.name} { } }",
        ]
    }

    def "detected sources are returned ordered by class name"() {
        expect:
        detector.getDeclaredSources(SourcesNotDeclaredAlphabetically).toList() == [SourcesNotDeclaredAlphabetically.A, SourcesNotDeclaredAlphabetically.B]
    }

    private void addClass(GroovyClassLoader cl, String impl) {
        def type = cl.parseClass(impl)
        detector.getDeclaredSources(type)
        type = null
    }

}
