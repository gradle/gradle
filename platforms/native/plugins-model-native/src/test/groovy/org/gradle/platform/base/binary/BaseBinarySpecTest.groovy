/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base.binary


import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.sources.BaseLanguageSourceSet
import org.gradle.model.internal.core.ModelRuleExecutionException
import org.gradle.model.internal.core.MutableModelNode
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.ComponentSpec
import org.gradle.platform.base.ModelInstantiationException
import org.gradle.platform.base.PlatformBaseSpecification
import org.gradle.platform.base.component.BaseComponentFixtures
import org.gradle.platform.base.component.BaseComponentSpec
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier

class BaseBinarySpecTest extends PlatformBaseSpecification {
    def "cannot instantiate directly"() {
        when:
        new BaseBinarySpec() {}

        then:
        def e = thrown ModelInstantiationException
        e.message == "Direct instantiation of a BaseBinarySpec is not permitted. Use a @ComponentType rule instead."
    }

    def "binary has name and sensible display name"() {
        def binary = create(SampleBinary, MySampleBinary, "sampleBinary")

        expect:
        binary instanceof SampleBinary
        binary.name == "sampleBinary"
        binary.projectScopedName == "sampleBinary"
        binary.displayName == "SampleBinary 'sampleBinary'"
        binary.namingScheme.description == "sample binary 'sampleBinary'"
    }

    def "qualifies project scoped named and display name using owners name"() {
        def component = BaseComponentFixtures.createNode(SampleComponent, MySampleComponent, new DefaultComponentSpecIdentifier("path", "sample"))
        def binary = create(SampleBinary, MySampleBinary, "unitTest", component)

        expect:
        binary.name == "unitTest"
        binary.projectScopedName == "sampleUnitTest"
        binary.displayName == "SampleBinary 'sample:unitTest'"
        binary.namingScheme.description == "sample binary 'sample:unitTest'"
    }

    def "create fails if subtype does not have a public no-args constructor"() {
        when:
        create(SampleBinary, MyConstructedBinary, "sampleBinary")

        then:
        def e = thrown ModelRuleExecutionException
        e.cause instanceof ModelInstantiationException
        e.cause.message == "Could not create binary of type SampleBinary"
        e.cause.cause instanceof IllegalArgumentException
        e.cause.cause.message == "Unable to determine constructor argument #1: missing parameter of type String, or no service of type String."
    }

    def "can own source sets"() {
        def binary = create(SampleBinary, MySampleBinary, "sampleBinary")
        def customSourceSet = Stub(LanguageSourceSet) {
            getName() >> "custom"
        }
        def inputSourceSet = Stub(LanguageSourceSet) {
            getName() >> "input"
        }

        when:
        binary.sources.put("custom", customSourceSet)

        then:
        binary.sources.values()*.name == ["custom"]

        when:
        binary.inputs.add inputSourceSet

        then:
        binary.sources.values()*.name == ["custom"]
        binary.inputs*.name == ["input"]
    }

    private <T extends BinarySpec, I extends BaseBinarySpec> T create(Class<T> type, Class<I> implType, String name, MutableModelNode componentNode = null) {
        BaseBinaryFixtures.create(type, implType, name, componentNode)
    }

    interface SampleComponent extends ComponentSpec {}

    static class MySampleComponent extends BaseComponentSpec implements SampleComponent {}

    interface SampleBinary extends BinarySpec {}

    static class MySampleBinary extends BaseBinarySpec implements SampleBinary {
    }
    static class MyConstructedBinary extends BaseBinarySpec implements SampleBinary {
        MyConstructedBinary(String arg) {}
    }

    static class CustomSourceSet extends BaseLanguageSourceSet {}
}
