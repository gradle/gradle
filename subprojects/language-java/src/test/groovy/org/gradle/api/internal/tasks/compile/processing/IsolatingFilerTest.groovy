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

import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessorResult
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource

import javax.tools.StandardLocation

class IsolatingFilerTest extends IncrementalFilerTest {

    @Override
    IncrementalProcessingStrategy getStrategy(AnnotationProcessorResult result) {
        new IsolatingProcessingStrategy(result)
    }

    def "does a full rebuild when no originating elements are given for a type"() {
        when:
        filer.createSourceFile("Foo")

        then:
        result.fullRebuildCause == "the generated type 'Foo' must have exactly one originating element, but had 0"
    }

    def "does a full rebuild when no originating elements are given for a resource"() {
        when:
        filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "foo.txt")

        then:
        result.fullRebuildCause == "the generated resource 'foo.txt in SOURCE_OUTPUT' must have exactly one originating element, but had 0"
    }

    def "does a full rebuild when too many originating elements are given for a type"() {
        when:
        filer.createSourceFile("Foo", type("Bar"), type("Baz"))

        then:
        result.fullRebuildCause == "the generated type 'Foo' must have exactly one originating element, but had 2"
    }

    def "does a full rebuild when too many originating elements are given for a resource"() {
        when:
        filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "foo.txt", type("Bar"), type("Baz"))

        then:
        result.fullRebuildCause == "the generated resource 'foo.txt in SOURCE_OUTPUT' must have exactly one originating element, but had 2"
    }

    def "can have multiple originating elements coming from the same type"() {
        when:
        filer.createSourceFile("Foo", methodInside("Bar"), methodInside("Bar"))
        filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "foo.txt", methodInside("Bar"), methodInside("Bar"))

        then:
        !result.fullRebuildCause
    }

    def "packages are valid originating elements"() {
        when:
        filer.createSourceFile("Foo", pkg("fizz"))
        filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "foo.txt", pkg("fizz"))

        then:
        result.generatedTypesWithIsolatedOrigin.size() == 1
        result.generatedTypesWithIsolatedOrigin["fizz.package-info"] == ["Foo"] as Set
        result.generatedResourcesWithIsolatedOrigin.size() == 1
        result.generatedResourcesWithIsolatedOrigin["fizz.package-info"] == [sourceResource("foo.txt")] as Set
    }

    def "adds originating types to the processing result"() {
        when:
        filer.createSourceFile("Foo", pkg("pkg"), type("A"), methodInside("B"))
        filer.createSourceFile("Bar", type("B"))

        filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "foo.txt", pkg("pkg"), type("A"), methodInside("B"))
        filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "bar.txt", type("B"))

        then:
        result.generatedTypesWithIsolatedOrigin.size() == 3
        result.generatedTypesWithIsolatedOrigin["A"] == ["Foo"] as Set
        result.generatedTypesWithIsolatedOrigin["pkg.package-info"] == ["Foo"] as Set
        result.generatedTypesWithIsolatedOrigin["B"] == ["Foo", "Bar"] as Set

        def foo = sourceResource("foo.txt")
        def bar = sourceResource("bar.txt")
        result.generatedResourcesWithIsolatedOrigin.size() == 3
        result.generatedResourcesWithIsolatedOrigin["A"] == [foo] as Set
        result.generatedResourcesWithIsolatedOrigin["pkg.package-info"] == [foo] as Set
        result.generatedResourcesWithIsolatedOrigin["B"] == [foo, bar] as Set
    }

    def "handles resources in the three StandardLocation output locations"() {
        when:
        filer.createResource(inputLocation, "", "foo.txt", type("A"))

        then:
        result.generatedResourcesWithIsolatedOrigin["A"] == [new GeneratedResource(resultLocation, "foo.txt")] as Set

        where:
        inputLocation                         | resultLocation
        StandardLocation.SOURCE_OUTPUT        | GeneratedResource.Location.SOURCE_OUTPUT
        StandardLocation.CLASS_OUTPUT         | GeneratedResource.Location.CLASS_OUTPUT
        StandardLocation.NATIVE_HEADER_OUTPUT | GeneratedResource.Location.NATIVE_HEADER_OUTPUT
    }
}
