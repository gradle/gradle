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

package org.gradle.nativeplatform.internal.configure

import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.nativeplatform.internal.DefaultFlavor
import org.gradle.nativeplatform.internal.DefaultFlavorContainer
import org.gradle.nativeplatform.plugins.NativeComponentModelPlugin
import org.gradle.util.TestUtil
import spock.lang.Specification

class CreateDefaultFlavorsTest extends Specification {
    def flavorContainer = new DefaultFlavorContainer(TestUtil.instantiatorFactory().decorateLenient(), CollectionCallbackActionDecorator.NOOP)
    def rule = new NativeComponentModelPlugin.Rules()

    def "has a single default flavor when not configured"() {
        when:
        rule.createDefaultFlavor(flavorContainer)

        then:
        flavorContainer.size() == 1
        flavorNames == [DefaultFlavor.DEFAULT] as Set
    }

    def "configured flavors overwrite default flavor"() {
        when:
        flavorContainer.configure {
            flavor1 {}
            flavor2 {}
        }
        and:
        rule.createDefaultFlavor(flavorContainer)

        then:
        flavorNames == ["flavor1", "flavor2"] as Set
    }

    def "can explicitly add flavor named 'default'"() {
        when:
        flavorContainer.configure {
            flavor1 {}
            'default' {}
            flavor2 {}
        }
        and:
        rule.createDefaultFlavor(flavorContainer)

        then:
        flavorNames == [DefaultFlavor.DEFAULT, "flavor1", "flavor2"] as Set

    }

    def getFlavorNames() {
        return flavorContainer.collect { it.name } as Set
    }
}
