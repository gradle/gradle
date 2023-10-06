/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model

import org.gradle.model.internal.core.ModelRuleExecutionException
import org.gradle.model.internal.fixture.ProjectRegistrySpec
import org.gradle.model.internal.inspect.ReadonlyImmutableManagedPropertyException

class ManagedNamedTest extends ProjectRegistrySpec {

    def "named struct has name property populated"() {
        when:
        registry.registerWithInitializer("foo", NamedThingInterface, nodeInitializerRegistry)

        then:
        registry.realize("foo", NamedThingInterface).name == "foo"

        when:
        registry.registerWithInitializer("bar", NamedThingInterface, nodeInitializerRegistry)

        then:
        registry.realize("bar", NamedThingInterface).name == "bar"
    }


    @Managed
    static abstract class NonNamedThing {
        abstract String getName()

        abstract void setName(String name)
    }

    def "named struct does not have name populated if does not implement named"() {
        when:
        registry.registerWithInitializer("foo", NonNamedThing, nodeInitializerRegistry)

        then:
        registry.realize("foo", NonNamedThing).name == null
    }

    @Managed
    static abstract class NonNamedThingNoSetter {
        abstract String getName()
    }

    def "name requires setter if not named"() {
        given:
        registry.registerWithInitializer("bar", NonNamedThingNoSetter, nodeInitializerRegistry)

        when:
        registry.realize("bar", NonNamedThingNoSetter)

        then:
        def ex = thrown(ModelRuleExecutionException)
        ex.cause instanceof ReadonlyImmutableManagedPropertyException
    }
}
