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
        new BuildInitSpecRegistry().isEmpty()
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
        !registry.isEmpty()
        registry.getAllSpecs() == [spec1, spec2]
        registry.getGeneratorForSpec(spec1) == generator.class
        registry.getGeneratorForSpec(spec2) == generator.class

        when:
        registry.getGeneratorForSpec(new TestBuildInitSpec("type3", "Some Third Name"))

        then: "not loaded specs can't be found"
        def e = thrown(IllegalStateException)
        e.message == "Spec: 'Some Third Name' with type: 'type3' is not registered!"

        when:
        registry.getGeneratorForSpec(new TestBuildInitSpec("type3", "My Name"))

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
        def result = registry.getSpecByType("type1")

        then:
        result == spec1

        when:
        registry.getSpecByType("unknown")

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
        !registry.isEmpty()
        registry.getAllSpecs() == [spec1, spec2]
        registry.getSpecByType("type") == spec1
        registry.getSpecByType("type-2") == spec2
        registry.getGeneratorForSpec(spec1) == generator.class
        registry.getGeneratorForSpec(spec2) == generator.class
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
}
