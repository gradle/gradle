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
import spock.lang.Specification

import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import javax.tools.StandardLocation

abstract class IncrementalFilerTest extends Specification {
    Filer delegate = Stub(Filer)
    AnnotationProcessingResult result = new AnnotationProcessingResult()
    Messager messager = Mock(Messager)
    Filer filer

    def setup() {
        filer = createFiler(delegate, result, messager)
    }

    abstract Filer createFiler(Filer filer, AnnotationProcessingResult result, Messager messager)

    def "fails when a package element is given as an originating element"() {
        when:
        filer.createSourceFile("Foo", pkg("fizz"))

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, "Incremental annotation processors must use types (or elements contained in types) as originating elements.")
    }

    def "fails when trying to read resources"() {
        when:
        filer.getResource(StandardLocation.SOURCE_OUTPUT, "", "foo.txt")

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, "Incremental annotation processors are not allowed to read resources.")
    }

    def "fails when trying to write resources"() {
        when:
        filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "foo.txt")

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, "Incremental annotation processors are not allowed to create resources.")
    }

    def "adds originating types to the processing result"() {
        when:
        filer.createSourceFile("Foo", pkg("pkg"), type("A"), methodInside("B"))
        filer.createSourceFile("Bar", type("B"))

        then:
        result.generatedTypesByOrigin.size() == 2
        result.generatedTypesByOrigin["A"] == ["Foo"] as Set
        result.generatedTypesByOrigin["B"] == ["Foo", "Bar"] as Set

    }

    PackageElement pkg(String packageName) {
        Stub(PackageElement) {
            getEnclosingElement() >> null
        }
    }

    TypeElement type(String typeName) {
        Stub(TypeElement) {
            getEnclosingElement() >> pkg("")
            getQualifiedName() >> Stub(Name) {
                toString() >> typeName
            }
        }
    }

    ExecutableElement methodInside(String typeName) {
        Stub(ExecutableElement) {
            getEnclosingElement() >> type(typeName)
        }
    }
}
