/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativebinaries.internal

import org.gradle.api.internal.AsmBackedClassGenerator
import org.gradle.api.internal.ClassGeneratorBackedInstantiator
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.DefaultFunctionalSourceSet
import spock.lang.Specification

class DefaultNativeComponentTest extends Specification {
    def instantiator = new ClassGeneratorBackedInstantiator(new AsmBackedClassGenerator(), new DirectInstantiator())
    def id = new NativeBuildComponentIdentifier("project", "name")
    def component = new DefaultNativeComponent(id, instantiator)

    def "uses all source sets from a functional source set"() {
        given:
        def functionalSourceSet = new DefaultFunctionalSourceSet("func", instantiator)
        def sourceSet1 = Stub(LanguageSourceSet) {
            getName() >> "ss1"
        }
        def sourceSet2 = Stub(LanguageSourceSet) {
            getName() >> "ss2"
        }

        when:
        functionalSourceSet.add(sourceSet1)
        functionalSourceSet.add(sourceSet2)

        and:
        component.source functionalSourceSet

        then:
        component.source.contains(sourceSet1)
        component.source.contains(sourceSet2)
    }

    def "flavors can be chosen and will replace default flavor"() {
        when:
        component.targetFlavors "flavor1", "flavor2"

        and:
        component.targetFlavors("flavor3")

        then:
        component.chooseFlavors([flavor("flavor1"), flavor("flavor2"), flavor("flavor3"), flavor("flavor4")] as Set)*.name == ["flavor1", "flavor2", "flavor3"]
    }

    def flavor(String name) {
        new DefaultFlavor(name)
    }
}
