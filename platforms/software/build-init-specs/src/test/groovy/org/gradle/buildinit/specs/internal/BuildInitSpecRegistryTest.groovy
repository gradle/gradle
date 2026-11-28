/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.buildinit.specs.internal

import org.gradle.buildinit.specs.BuildInitGenerator
import org.gradle.builtinit.specs.internal.TestBuildInitSpec
import org.gradle.builtinit.specs.internal.TestBuildInitGenerator
import org.gradle.util.internal.TextUtil
import spock.lang.Specification

/**
 * Unit tests for {@link BuildInitSpecRegistry}.
 */
class BuildInitSpecRegistryTest extends Specification {
    def "empty registry is empty"() {
        expect:
        new BuildInitSpecRegistry().snapshot().isEmpty()
    }

    def "registry provides loaded specs"() {
        given:
        def generator = new TestBuildInitGenerator()
        def spec1 = new TestBuildInitSpec("type1", "My Name")
        def spec2 = new TestBuildInitSpec("type2", "My Other Name")
        def registry = new BuildInitSpecRegistry()

        when:
        registry.register(generator.class, [spec1, spec2])

        then: "loaded specs can be found"
        !registry.snapshot().isEmpty()
        registry.snapshot().getAllSpecs() == [spec1, spec2]
        registry.snapshot().getGeneratorForSpec(spec1) == generator.class
        registry.snapshot().getGeneratorForSpec(spec2) == generator.class

        when:
        registry.snapshot().getGeneratorForSpec(new TestBuildInitSpec("type3", "Some Third Name"))

        then: "not loaded specs can't be found"
        def e = thrown(IllegalStateException)
        e.message == "Spec: 'Some Third Name' with type: 'type3' is not registered!"

        when:
        registry.snapshot().getGeneratorForSpec(new TestBuildInitSpec("type3", "My Name"))

        then: "specs with the same display name can't be found - we're finding specs by type"
        e = thrown(IllegalStateException)
        e.message == "Spec: 'My Name' with type: 'type3' is not registered!"
    }

    def "registry can look up spec by type"() {
        given:
        def generator = new TestBuildInitGenerator()
        def spec1 = new TestBuildInitSpec("type1", "My Name")
        def spec2 = new TestBuildInitSpec("type2", "My Other Name")
        def registry = new BuildInitSpecRegistry()
        registry.register(generator.class, [spec1, spec2])

        when: "loaded specs can be found by type"
        def result = registry.snapshot().getSpecByType("type1")

        then:
        result == spec1

        when:
        registry.snapshot().getSpecByType("unknown")

        then: "Unknown spec type can't be found"
        def e = thrown(IllegalStateException)
        e.message == TextUtil.toPlatformLineSeparators("""Build init spec with type: 'unknown' was not found!
Known types:
 - type1
 - type2""")
    }

    def "multiple specs with same type cannot be registered"() {
        given:
        def generator = new TestBuildInitGenerator()
        def spec1 = new TestBuildInitSpec("type", "My Name")
        def spec2 = new TestBuildInitSpec("type", "My Other Name")
        def registry = new BuildInitSpecRegistry()

        when:
        registry.register(generator.class, [spec1, spec2])

        then:
        def e = thrown(IllegalStateException)
        e.message == "Spec: 'My Other Name' with type: 'type' cannot use same type as another spec already registered!"
    }

    def "multiple specs with same type cannot be registered across multiple calls"() {
        given:
        def generator = new TestBuildInitGenerator()
        def spec1 = new TestBuildInitSpec("type", "My Name")
        def spec2 = new TestBuildInitSpec("type", "My Other Name")
        def registry = new BuildInitSpecRegistry()

        when:
        registry.register(generator.class, [spec1])
        registry.register(generator.class, [spec2])

        then:
        def e = thrown(IllegalStateException)
        e.message == "Spec: 'My Other Name' with type: 'type' cannot use same type as another spec already registered!"
    }

    def "Additional specs can be registered to an existing generator across multiple calls"() {
        given:
        def generator = new TestBuildInitGenerator()
        def spec1 = new TestBuildInitSpec("type", "My Name")
        def spec2 = new TestBuildInitSpec("type-2", "My Other Name")
        def registry = new BuildInitSpecRegistry()

        when:
        registry.register(generator.class, [spec1])
        registry.register(generator.class, [spec2])

        then:
        !registry.snapshot().isEmpty()
        registry.snapshot().getAllSpecs() == [spec1, spec2]
        registry.snapshot().getSpecByType("type") == spec1
        registry.snapshot().getSpecByType("type-2") == spec2
        registry.snapshot().getGeneratorForSpec(spec1) == generator.class
        registry.snapshot().getGeneratorForSpec(spec2) == generator.class
    }

    def "multiple specs with same type cannot be registered to different generators"() {
        given:
        def generator1 = new TestBuildInitGenerator()
        def generator2 = Mock(BuildInitGenerator)
        def spec1 = new TestBuildInitSpec("type", "My Name")
        def spec2 = new TestBuildInitSpec("type", "My Other Name")
        def registry = new BuildInitSpecRegistry()

        when:
        registry.register(generator1.class, [spec1])
        registry.register(generator2.class, [spec2])

        then:
        def e = thrown(IllegalStateException)
        e.message == "Spec: 'My Other Name' with type: 'type' cannot use same type as another spec already registered!"
    }

    def "multiple specs with same type cannot be registered to different generators across multiple register calls"() {
        given:
        def generator1 = new TestBuildInitGenerator()
        def generator2 = Mock(BuildInitGenerator)
        def spec1 = new TestBuildInitSpec("type", "My Name")
        def spec2 = new TestBuildInitSpec("type", "My Other Name")
        def registry = new BuildInitSpecRegistry()

        when:
        registry.register(generator1.class, [spec1])
        registry.register(generator2.class, [spec2])

        then:
        def e = thrown(IllegalStateException)
        e.message == "Spec: 'My Other Name' with type: 'type' cannot use same type as another spec already registered!"
    }

    def "snapshot lists specs grouped by generator in generator registration order"() {
        given:
        def generator1 = new TestBuildInitGenerator()
        def generator2 = Mock(BuildInitGenerator)
        def specA = new TestBuildInitSpec("type-a", "A")
        def specB = new TestBuildInitSpec("type-b", "B")
        def specC = new TestBuildInitSpec("type-c", "C")
        def registry = new BuildInitSpecRegistry()

        when:
        registry.register(generator1.class, [specA])
        registry.register(generator2.class, [specB])
        registry.register(generator1.class, [specC])

        then:
        registry.snapshot().getAllSpecs() == [specA, specC, specB]
    }

    def "snapshot is not changed by later registrations"() {
        given:
        def generator = new TestBuildInitGenerator()
        def specA = new TestBuildInitSpec("type-a", "A")
        def specB = new TestBuildInitSpec("type-b", "B")
        def registry = new BuildInitSpecRegistry()
        registry.register(generator.class, [specA])
        def snapshot = registry.snapshot()

        when:
        registry.register(generator.class, [specB])

        then:
        snapshot.getAllSpecs() == [specA]
        registry.snapshot().getAllSpecs() == [specA, specB]
    }
}
