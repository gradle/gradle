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

class AggregatingFilerTest extends IncrementalFilerTest {

    @Override
    IncrementalProcessingStrategy getStrategy(AnnotationProcessorResult result) {
        new AggregatingProcessingStrategy(result)
    }

    def "can have zero originating elements"() {
        when:
        filer.createSourceFile("Foo")
        filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "foo.txt")

        then:
        !result.fullRebuildCause
    }

    def "can have many originating elements"() {
        when:
        filer.createSourceFile("Foo", type("Bar"), type("Baz"))
        filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "foo.txt", type("Bar"), type("Baz"))

        then:
        !result.fullRebuildCause
    }

    def "adds generated types to the processing result"() {
        when:
        filer.createSourceFile("Foo", pkg("pkg"), type("A"), methodInside("B"))
        filer.createSourceFile("Bar", type("B"))

        filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "foo.txt", pkg("pkg"), type("A"), methodInside("B"))
        filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "bar.txt", type("B"))

        then:
        result.generatedTypesWithIsolatedOrigin.isEmpty()
        result.generatedAggregatingTypes == ["Foo", "Bar"] as Set

        result.generatedResourcesWithIsolatedOrigin.isEmpty()
        result.generatedAggregatingResources == [sourceResource("foo.txt"), sourceResource("bar.txt")] as Set
    }

    def "handles resources in the three StandardLocation output locations"() {
        when:
        filer.createResource(inputLocation, "com.enterprise.software", "foo.txt", type("A"))

        then:
        result.generatedAggregatingResources == [new GeneratedResource(resultLocation, "com/enterprise/software/foo.txt")] as Set

        where:
        inputLocation                         | resultLocation
        StandardLocation.SOURCE_OUTPUT        | GeneratedResource.Location.SOURCE_OUTPUT
        StandardLocation.CLASS_OUTPUT         | GeneratedResource.Location.CLASS_OUTPUT
        StandardLocation.NATIVE_HEADER_OUTPUT | GeneratedResource.Location.NATIVE_HEADER_OUTPUT
    }

    def "resources with same path but different location are distinct"() {
        when:
        filer.createResource(StandardLocation.SOURCE_OUTPUT,        "com.enterprise.software", "foo.txt", type("A"))
        filer.createResource(StandardLocation.CLASS_OUTPUT,         "com.enterprise.software", "foo.txt", type("A"))
        filer.createResource(StandardLocation.NATIVE_HEADER_OUTPUT, "com.enterprise.software", "foo.txt", type("A"))

        then:
        result.generatedAggregatingResources == [GeneratedResource.Location.SOURCE_OUTPUT, GeneratedResource.Location.CLASS_OUTPUT, GeneratedResource.Location.NATIVE_HEADER_OUTPUT]
            .collect { new GeneratedResource(it, "com/enterprise/software/foo.txt") } as Set
    }
}
