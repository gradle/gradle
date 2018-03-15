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

package org.gradle.api.internal.tasks.compile.processing

import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult

import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.tools.Diagnostic

class IsolatingFilerTest extends IncrementalFilerTest {

    @Override
    Filer createFiler(Filer filer, AnnotationProcessingResult result, Messager messager) {
        new IsolatingFiler(delegate, result, messager)
    }

    def "fails when no originating elements are given"() {
        when:
        filer.createSourceFile("Foo")

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, "Generated type 'Foo' must have exactly one originating element, but had 0.")
    }

    def "fails when too many originating elements are given"() {
        when:
        filer.createSourceFile("Foo", type("Bar"), type("Baz"))

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, "Generated type 'Foo' must have exactly one originating element, but had 2.")
    }

    def "does not fail when all originating elements come from the same tpye"() {
        when:
        filer.createSourceFile("Foo", methodInside("Bar"), methodInside("Bar"))

        then:
        0 * messager._
    }

    def "packages are valid originating elements"() {
        when:
        filer.createSourceFile("Foo", pkg("fizz"))

        then:
        result.generatedTypesByOrigin.size() == 1
        result.generatedTypesByOrigin["fizz.package-info"] == ["Foo"] as Set
    }

    def "adds originating types to the processing result"() {
        when:
        filer.createSourceFile("Foo", pkg("pkg"), type("A"), methodInside("B"))
        filer.createSourceFile("Bar", type("B"))

        then:
        result.generatedTypesByOrigin.size() == 3
        result.generatedTypesByOrigin["A"] == ["Foo"] as Set
        result.generatedTypesByOrigin["pkg.package-info"] == ["Foo"] as Set
        result.generatedTypesByOrigin["B"] == ["Foo", "Bar"] as Set
    }
}
