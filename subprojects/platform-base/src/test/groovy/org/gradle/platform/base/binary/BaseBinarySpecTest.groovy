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

import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.ProjectSourceSet
import org.gradle.language.base.internal.DefaultFunctionalSourceSet
import org.gradle.language.base.sources.BaseLanguageSourceSet
import org.gradle.platform.base.ModelInstantiationException
import spock.lang.Specification

class BaseBinarySpecTest extends Specification {
    def instantiator = DirectInstantiator.INSTANCE

    def "cannot instantiate directly"() {
        when:
        new BaseBinarySpec() {}

        then:
        def e = thrown ModelInstantiationException
        e.message == "Direct instantiation of a BaseBinarySpec is not permitted. Use a BinaryTypeBuilder instead."
    }

    def "cannot create instance of base class"() {
        when:
        BaseBinarySpec.create(BaseBinarySpec, "sampleBinary", instantiator, Mock(ITaskFactory))

        then:
        def e = thrown ModelInstantiationException
        e.message == "Cannot create instance of abstract class BaseBinarySpec."
    }

    def "binary has name and sensible display name"() {
        def binary = BaseBinarySpec.create(MySampleBinary, "sampleBinary", instantiator, Mock(ITaskFactory))

        expect:
        binary.class == MySampleBinary
        binary.name == "sampleBinary"
        binary.displayName == "MySampleBinary 'sampleBinary'"
    }

    def "create fails if subtype does not have a public no-args constructor"() {
        when:
        BaseBinarySpec.create(MyConstructedBinary, "sampleBinary", instantiator, Mock(ITaskFactory))

        then:
        def e = thrown ModelInstantiationException
        e.message == "Could not create binary of type MyConstructedBinary"
        e.cause instanceof IllegalArgumentException
        e.cause.message.startsWith "Could not find any public constructor for class"
    }

    def "can own source sets"() {
        def binary = BaseBinarySpec.create(MySampleBinary, "sampleBinary", instantiator, Mock(ITaskFactory))
        def functionalSourceSet = new DefaultFunctionalSourceSet("main", instantiator, Stub(ProjectSourceSet))
        binary.binarySources = functionalSourceSet
        def sourceSet1 = Stub(LanguageSourceSet) {
            getName() >> "ss1"
        }
        def sourceSet2 = Stub(LanguageSourceSet) {
            getName() >> "ss2"
        }
        def sourceSet3 = Stub(LanguageSourceSet) {
            getName() >> "ss3"
        }

        when:
        functionalSourceSet.add(sourceSet1)

        then:
        functionalSourceSet*.name == ["ss1"]
        binary.sources.values()*.name == ["ss1"]
        binary.inputs*.name == ["ss1"]

        when:
        binary.addSourceSet(sourceSet2)

        then:
        functionalSourceSet*.name == ["ss1", "ss2"]
        binary.sources.values()*.name == ["ss1", "ss2"]
        binary.inputs*.name == ["ss1", "ss2"]

        when:
        binary.inputs.add(sourceSet3)

        then:
        functionalSourceSet*.name == ["ss1", "ss2"]
        binary.sources.values()*.name == ["ss1", "ss2"]
        binary.inputs*.name == ["ss1", "ss2", "ss3"]
    }

    static class MySampleBinary extends BaseBinarySpec {
    }
    static class MyConstructedBinary extends BaseBinarySpec {
        MyConstructedBinary(String arg) {}
    }

    static class CustomSourceSet extends BaseLanguageSourceSet {}
}
