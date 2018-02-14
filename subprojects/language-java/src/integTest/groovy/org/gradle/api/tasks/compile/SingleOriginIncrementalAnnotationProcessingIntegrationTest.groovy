/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.tasks.compile

import org.gradle.api.internal.tasks.compile.processing.IncrementalAnnotationProcessorType
import org.gradle.language.fixtures.HelperProcessorFixture
import org.gradle.language.fixtures.NonIncrementalProcessorFixture
import org.gradle.language.fixtures.ServiceRegistryProcessorFixture

class SingleOriginIncrementalAnnotationProcessingIntegrationTest extends AbstractIncrementalAnnotationProcessingIntegrationTest {

    @Override
    def setup() {
        withProcessor(new HelperProcessorFixture())
    }

    def "all sources are recompiled when any class changes"() {
        given:
        def a = java "@Helper class A {}"
        java "class B {}"

        outputs.snapshot { run "compileJava" }

        when:
        a.text = "@Helper class A { public void foo() {} }"
        run "compileJava"

        then:
        outputs.recompiledClasses("A", "AHelper", "B")
    }

    def "the user is informed about non-incremental processors"() {
        given:
        def a = java "@Helper class A {}"
        run "compileJava"

        when:
        a.text = "@Helper class A { public void foo() {} }"
        run "compileJava", "--info"

        then:
        output.contains("The following annotation processors don't support incremental compilation:")
        output.contains("Processor (type: SINGLE_ORIGIN)")
    }

    def "processors must provide an originating element for each source element"() {
        given:
        withProcessor(new NonIncrementalProcessorFixture().withDeclaredType(IncrementalAnnotationProcessorType.SINGLE_ORIGIN))
        java "@Thing class A {}"

        expect:
        fails "compileJava"

        and:
        errorOutput.contains("Generated file 'AThing' must have exactly one originating element, but had 0.")
    }

    def "processors can't access resources"() {
        given:
        withProcessor(new NonIncrementalProcessorFixture().withDeclaredType(IncrementalAnnotationProcessorType.SINGLE_ORIGIN))
        java "@Thing class A {}"

        expect:
        fails "compileJava"

        and:
        errorOutput.contains("Incremental annotation processors are not allowed to read resources.")
        errorOutput.contains("Incremental annotation processors are not allowed to create resources.")
    }

    def "processors cannot provide multiple originating elements"() {
        given:
        withProcessor(new ServiceRegistryProcessorFixture().withDeclaredType(IncrementalAnnotationProcessorType.SINGLE_ORIGIN))
        java "@Service class A {}"
        java "@Service class B {}"

        expect:
        fails "compileJava"

        and:
        errorOutput.contains("Generated file 'ServiceRegistry' must have exactly one originating element, but had 2.")
    }
}
