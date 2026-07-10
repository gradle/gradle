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

import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult
import org.gradle.api.specs.Spec
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.internal.typeconversion.UnsupportedNotationException
import spock.lang.Specification

import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class DependencyResultSpecNotationParserSpec extends Specification {

    NotationParser<Object, Spec<DependencyResult>> parser = DependencyResultSpecNotationConverter.parser()

    def "accepts closures"() {
        given:
        def mockito = newDependency('org.mockito', 'mockito-core')
        def other = newDependency('org.mockito', 'other')

        when:
        def spec = parser.parseNotation( { it.requested.module == 'mockito-core' } )

        then:
        spec.isSatisfiedBy(mockito)
        !spec.isSatisfiedBy(other)
    }

    def "accepts Strings"() {
        given:
        def mockito = newDependency('org.mockito', 'mockito-core')
        def other = newDependency('org.mockito', 'other')

        when:
        def spec = parser.parseNotation('mockito-core')

        then:
        spec.isSatisfiedBy(mockito)
        !spec.isSatisfiedBy(other)
    }

    def "accepts specs"() {
        given:
        def mockito = newDependency('org.mockito', 'mockito-core')
        def other = newDependency('org.mockito', 'other')

        when:
        def spec = parser.parseNotation(new Spec<DependencyResult>() {
            boolean isSatisfiedBy(DependencyResult element) {
                return element.requested.module == 'mockito-core'
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
        def ex = thrown(UnsupportedNotationException)
        ex.message == toPlatformLineSeparators("""Cannot convert the provided notation to an object of type Spec: [not supported].
The following types/formats are supported:
  - Instances of Spec.
  - Closure that returns boolean and takes a single DependencyResult as a parameter.
  - Non-empty String or CharSequence value, for example 'some-lib' or 'org.libs:some-lib'.

Please check the input for the DependencyInsight.dependency element.""")
    }

    def "does not accept empty Strings"() {
        when:
        parser.parseNotation('')
        then:
        thrown(UnsupportedNotationException)

        when:
        parser.parseNotation(' ')
        then:
        thrown(UnsupportedNotationException)
    }

    DefaultResolvedDependencyResult newDependency(String group='a', String module='a', String version='1', String selectedVersion='1') {
        new DefaultResolvedDependencyResult(newSelector(group, module, version), false, newModule(), newModule(group, module, selectedVersion), Mock(ResolvedVariantResult))
    }

    private ResolvedComponentResult newModule(String group = "a", String module = "a", String version = "1") {
        Mock(ResolvedComponentResult) {
            getModuleVersion() >> DefaultModuleVersionIdentifier.newId(group, module, version)
        }
    }

    static ModuleComponentSelector newSelector(String group, String module, String version) {
        DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(group, module), version)
    }

}
