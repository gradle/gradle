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

import org.gradle.nativeplatform.NativeComponentSpec
import org.gradle.platform.base.component.BaseComponentFixtures
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import spock.lang.Specification

class DefaultNativeComponentTest extends Specification {
    def id = new DefaultComponentSpecIdentifier("project", "name")
    def component

    def setup(){
        component = BaseComponentFixtures.create(NativeComponentSpec, TestNativeComponentSpec, id)
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
    }

    def flavor(String name) {
        new DefaultFlavor(name)
    }
}
