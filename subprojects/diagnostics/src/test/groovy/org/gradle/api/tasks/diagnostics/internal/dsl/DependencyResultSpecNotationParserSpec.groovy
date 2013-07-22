/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.dsl

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.internal.artifacts.result.ResolutionResultDataBuilder
import org.gradle.api.internal.notations.api.NotationParser
import org.gradle.api.specs.Spec
import spock.lang.Specification

class DependencyResultSpecNotationParserSpec extends Specification {

    NotationParser<Spec<DependencyResult>> parser = DependencyResultSpecNotationParser.create()

    def "accepts closures"() {
        given:
        def mockito = ResolutionResultDataBuilder.newDependency('org.mockito', 'mockito-core')
        def other = ResolutionResultDataBuilder.newDependency('org.mockito', 'other')

        when:
        def spec = parser.parseNotation( { it.requested.name == 'mockito-core' } )

        then:
        spec.isSatisfiedBy(mockito)
        !spec.isSatisfiedBy(other)
    }

    def "accepts Strings"() {
        given:
        def mockito = ResolutionResultDataBuilder.newDependency('org.mockito', 'mockito-core')
        def other = ResolutionResultDataBuilder.newDependency('org.mockito', 'other')

        when:
        def spec = parser.parseNotation('mockito-core')

        then:
        spec.isSatisfiedBy(mockito)
        !spec.isSatisfiedBy(other)
    }

    def "accepts specs"() {
        given:
        def mockito = ResolutionResultDataBuilder.newDependency('org.mockito', 'mockito-core')
        def other = ResolutionResultDataBuilder.newDependency('org.mockito', 'other')

        when:
        def spec = parser.parseNotation(new Spec<DependencyResult>() {
            boolean isSatisfiedBy(DependencyResult element) {
                return element.getRequested().getName().equals('mockito-core')
            }
        })

        then:
        spec.isSatisfiedBy(mockito)
        !spec.isSatisfiedBy(other)
    }

    def "fails neatly for unknown notations"() {
        when:
        parser.parseNotation(['not supported'])

        then:
        def ex = thrown(InvalidUserDataException)
        ex.message.contains 'not supported'
        ex.message.contains 'DependencyInsight.dependency'
    }

    def "does not accept empty Strings"() {
        when:
        parser.parseNotation('')
        then:
        thrown(InvalidUserDataException)

        when:
        parser.parseNotation(' ')
        then:
        thrown(InvalidUserDataException)
    }
}