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

package org.gradle.nativeplatform.internal

import org.gradle.api.internal.AsmBackedClassGenerator
import org.gradle.api.internal.ClassGeneratorBackedInstantiator
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.base.ProjectSourceSet
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.platform.base.component.BaseComponentFixtures
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import spock.lang.Specification

class DefaultNativeComponentTest extends Specification {
    def instantiator = new ClassGeneratorBackedInstantiator(new AsmBackedClassGenerator(), DirectInstantiator.INSTANCE)
    def id = new DefaultComponentSpecIdentifier("project", "name")
    def component

    def setup(){
        component = BaseComponentFixtures.create(TestNativeComponentSpec, new ModelRegistryHelper(), id, Stub(ProjectSourceSet), instantiator)
    }

    def "flavors can be chosen and will replace default flavor"() {
        when:
        component.targetFlavors "flavor1", "flavor2"

        and:
        component.targetFlavors("flavor3")

        then:
        component.chooseFlavors([flavor("flavor1"), flavor("flavor2"), flavor("flavor3"), flavor("flavor4")] as Set)*.name == ["flavor1", "flavor2", "flavor3"]
    }

    static class TestNativeComponentSpec extends AbstractTargetedNativeComponentSpec {
        String getDisplayName() {
            return "test component"
        }
    }

    def flavor(String name) {
        new DefaultFlavor(name)
    }
}
