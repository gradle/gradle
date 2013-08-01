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



package org.gradle.nativecode.base.internal

import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.nativecode.base.Flavor
import spock.lang.Specification

class DefaultFlavorContainerTest extends Specification {
    def flavorContainer = new DefaultFlavorContainer(new DirectInstantiator())

    def "has a single default flavor when not configured"() {
        expect:
        flavorContainer.size() == 1
        flavorContainer == [Flavor.DEFAULT] as Set
    }

    def "configured flavors overwrite default flavor"() {
        when:
        flavorContainer.configure {
            flavor1 {}
            flavor2 {}
        }

        then:
        flavorContainer.size() == 2
        flavorContainer == [new DefaultFlavor("flavor1"), new DefaultFlavor("flavor2")] as Set

    }

    def "can explicitly add flavor named 'default'"() {
        when:
        flavorContainer.configure {
            flavor1 {}
            it.'default' {}
            flavor2 {}
        }

        then:
        flavorContainer.size() == 3
        flavorContainer == [new DefaultFlavor("flavor1"), new DefaultFlavor("flavor2"), Flavor.DEFAULT] as Set

    }
}
