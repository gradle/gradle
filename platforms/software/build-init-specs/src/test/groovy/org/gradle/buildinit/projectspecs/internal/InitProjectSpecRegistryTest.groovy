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
//file:noinspection GroovyConstructorNamedArguments

package org.gradle.buildinit.projectspecs.internal

import org.gradle.api.logging.Logger
import org.gradle.buildinit.projectspecs.InitProjectGenerator
import org.gradle.buildinit.projectspecs.InitProjectSpec
import org.gradle.builtinit.projectspecs.internal.TestInitProjectGenerator
import org.gradle.builtinit.projectspecs.internal.TestInitProjectSource
import org.gradle.builtinit.projectspecs.internal.TestInitProjectSpec
import org.gradle.internal.logging.ToStringLogger
import org.gradle.util.internal.TextUtil
import spock.lang.Specification

/**
 * Unit tests for {@link InitProjectSpecRegistry}.
 */
class InitProjectSpecRegistryTest extends Specification {
    private final Logger logger = new ToStringLogger()

    def "empty registry is empty"() {
        expect:
        new InitProjectSpecRegistry().isEmpty()
    }

    def "registry provides loaded specs"() {
        given:
        def generator = new TestInitProjectGenerator()
        def spec1 = new TestInitProjectSpec("type1", "My Name")
        def spec2 = new TestInitProjectSpec("type2", "My Other Name")
        def registry = new InitProjectSpecRegistry()

        when:
        registry.register([(generator.class) : [spec1, spec2]])

        then: "loaded specs can be found"
        !registry.isEmpty()
        registry.getAllSpecs() == [spec1, spec2]
        registry.getGeneratorForSpec(spec1) == generator.class
        registry.getGeneratorForSpec(spec2) == generator.class

        when:
        registry.getGeneratorForSpec(new TestInitProjectSpec("type3", "Some Third Name"))

        then: "not loaded specs can't be found"
        def e = thrown(IllegalStateException)
        e.message == "Spec: 'Some Third Name' with type: 'type3' is not registered!"

        when:
        registry.getGeneratorForSpec(new TestInitProjectSpec("type3", "My Name"))

        then: "specs with the same display name can't be found - we're finding specs by type"
        e = thrown(IllegalStateException)
        e.message == "Spec: 'My Name' with type: 'type3' is not registered!"
    }

    def "registry can load specs from loader"() {
        given:
        InitProjectSpec spec = new TestInitProjectSpec("test", "Test Spec")
        InitProjectSpec spec2 = new TestInitProjectSpec("test2", "Other Test Spec")
        TestInitProjectSource.addSpecs(spec, spec2)

        and:
        InitProjectSpecLoader loader = new InitProjectSpecLoader(Thread.currentThread().contextClassLoader, logger)
        def registry = new InitProjectSpecRegistry()

        when:
        registry.register(loader)

        then: "loaded specs can be found"
        !registry.isEmpty()
        registry.getAllSpecs() == [spec, spec2]
        registry.getGeneratorForSpec(spec) == TestInitProjectGenerator
        registry.getGeneratorForSpec(spec2) == TestInitProjectGenerator

        when:
        registry.getGeneratorForSpec(new TestInitProjectSpec("test3", "Some Third Spec"))

        then: "not loaded specs can't be found"
        def e = thrown(IllegalStateException)
        e.message == "Spec: 'Some Third Spec' with type: 'test3' is not registered!"

        when:
        registry.getGeneratorForSpec(new TestInitProjectSpec("not-test", "Test Spec"))

        then: "specs with the same display name can't be found - we're finding specs by type"
        e = thrown(IllegalStateException)
        e.message == "Spec: 'Test Spec' with type: 'not-test' is not registered!"
    }

    def "registry can look up spec by type"() {
        given:
        def generator = new TestInitProjectGenerator()
        def spec1 = new TestInitProjectSpec("type1", "My Name")
        def spec2 = new TestInitProjectSpec("type2", "My Other Name")
        def registry = new InitProjectSpecRegistry()
        registry.register([(generator.class) : [spec1, spec2]])

        when: "loaded specs can be found by type"
        def result = registry.getSpecByType("type1")

        then:
        result == spec1

        when:
        registry.getSpecByType("unknown")

        then: "Unknown spec type can't be found"
        def e = thrown(IllegalStateException)
        e.message == TextUtil.toPlatformLineSeparators("""Project spec with type: 'unknown' was not found!
Known types:
 - type1
 - type2""")
    }

    def "multiple specs with same type cannot be registered"() {
        given:
        def generator = new TestInitProjectGenerator()
        def spec1 = new TestInitProjectSpec("type", "My Name")
        def spec2 = new TestInitProjectSpec("type", "My Other Name")
        def registry = new InitProjectSpecRegistry()

        when:
        registry.register([(generator.class) : [spec1, spec2]])

        then:
        def e = thrown(IllegalStateException)
        e.message == "Spec: 'My Other Name' with type: 'type' cannot use same type as another spec already registered!"
    }

    def "multiple specs with same type cannot be registered across multiple calls"() {
        given:
        def generator = new TestInitProjectGenerator()
        def spec1 = new TestInitProjectSpec("type", "My Name")
        def spec2 = new TestInitProjectSpec("type", "My Other Name")
        def registry = new InitProjectSpecRegistry()

        when:
        registry.register([(generator.class) : [spec1]])
        registry.register([(generator.class) : [spec2]])

        then:
        def e = thrown(IllegalStateException)
        e.message == "Spec: 'My Other Name' with type: 'type' cannot use same type as another spec already registered!"
    }

    def "Additional specs can be registered to an existing generator across multiple calls"() {
        given:
        def generator = new TestInitProjectGenerator()
        def spec1 = new TestInitProjectSpec("type", "My Name")
        def spec2 = new TestInitProjectSpec("type-2", "My Other Name")
        def registry = new InitProjectSpecRegistry()

        when:
        registry.register([(generator.class) : [spec1]])
        registry.register([(generator.class) : [spec2]])

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
        def generator1 = new TestInitProjectGenerator()
        def generator2 = Mock(InitProjectGenerator)
        def spec1 = new TestInitProjectSpec("type", "My Name")
        def spec2 = new TestInitProjectSpec("type", "My Other Name")
        def registry = new InitProjectSpecRegistry()

        when:
        registry.register([(generator1.class) : [spec1], (generator2.class) : [spec2]] )

        then:
        def e = thrown(IllegalStateException)
        e.message == "Spec: 'My Other Name' with type: 'type' cannot use same type as another spec already registered!"
    }

    def "multiple specs with same type cannot be registered to different generators across multiple register calls"() {
        given:
        def generator1 = new TestInitProjectGenerator()
        def generator2 = Mock(InitProjectGenerator)
        def spec1 = new TestInitProjectSpec("type", "My Name")
        def spec2 = new TestInitProjectSpec("type", "My Other Name")
        def registry = new InitProjectSpecRegistry()

        when:
        registry.register([(generator1.class) : [spec1]] )
        registry.register([(generator2.class) : [spec2]] )

        then:
        def e = thrown(IllegalStateException)
        e.message == "Spec: 'My Other Name' with type: 'type' cannot use same type as another spec already registered!"
    }
}
