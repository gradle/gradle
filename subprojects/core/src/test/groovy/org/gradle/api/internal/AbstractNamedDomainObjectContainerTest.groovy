/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal

import org.junit.Ignore
import org.junit.Test
import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat
import static org.junit.Assert.fail
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.reflect.DirectInstantiator

class AbstractNamedDomainObjectContainerTest {
    private final Instantiator instantiator = new ClassGeneratorBackedInstantiator(new AsmBackedClassGenerator(), new DirectInstantiator())
    private final AbstractNamedDomainObjectContainer container = instantiator.newInstance(TestContainer.class, instantiator)

    @Test
    public void isDynamicObjectAware() {
        assertThat(container, instanceOf(DynamicObjectAware));
    }

    @Test
    public void canAddObjectWithName() {
        container.create('obj')
        assertThat(container.getByName('obj'), equalTo(['obj']))
    }

    @Test
    public void canAddAndConfigureAnObjectWithName() {
        container.create('obj') {
            add(1)
            add('value')
        }
        assertThat(container.getByName('obj'), equalTo(['obj', 1, 'value']))
    }

    @Test
    public void canUseMaybeCreateToFindOrCreateObjectWithName() {
        def created = container.maybeCreate('obj')
        assertThat(container.getByName('obj'), equalTo(['obj']))

        def fetched = container.maybeCreate('obj')
        assertThat(fetched, sameInstance(created))
    }

    @Test
    public void failsToAddObjectWhenObjectWithSameNameAlreadyInContainer() {
        container.create('obj')

        try {
            container.create('obj')
            fail()
        } catch (org.gradle.api.InvalidUserDataException e) {
            assertThat(e.message, equalTo('Cannot add a TestObject with name \'obj\' as a TestObject with that name already exists.'))
        }
    }

    @Test
    public void canConfigureExistingObject() {
        container.create('list1')
        container.configure {
            list1 { add(1) }
        }
        assertThat(container.list1, equalTo(['list1', 1]))
    }

    @Test
    public void propagatesNestedMissingMethodException() {
        container.create('list1')
        try {
            container.configure {
                list1 { unknown { anotherUnknown(2) } }
            }
        } catch (groovy.lang.MissingMethodException e) {
            assertThat(e.method, equalTo('unknown'))
            assertThat(e.type, equalTo(TestObject))
        }
    }

    @Test
    public void propagatesMethodInvocationException() {
        RuntimeException failure = new RuntimeException()
        try {
            container.configure {
                list1 { throw failure }
            }
        } catch (RuntimeException e) {
            assertThat(e, sameInstance(failure))
        }
    }

    @Test
    public void implicitlyAddsAnObjectWhenContainerIsBeingConfigured() {
        container.configure {
            list1
            list2 { add(1) }
        }
        assertThat(container.list1, equalTo(['list1']))
        assertThat(container.list2, equalTo(['list2', 1]))
    }

    @Test
    public void canReferToPropertiesAndMethodsOfOwner() {
        new DynamicOwner().configure(container)
        assertThat(container.asMap.keySet(), equalTo(['list1', 'list2'] as Set))
        assertThat(container.list1, equalTo(['list1', 'dynamicProp', 'ownerProp', 'ownerMethod', 'dynamicMethod', 'dynamicMethod', 1, 'prop', 'testObjectDynamicMethod']))
        assertThat(container.list1.prop, equalTo('prop'))
        assertThat(container.list2, equalTo(['list2', container.list1]))
    }

    @Test @Ignore
    public void canUseAnItemCalledMainInAScript() {
        Script script = new GroovyShell().parse("""import org.gradle.util.ConfigureUtil
            c.configure {
                run
                main { add(1) }
            }

""")
        script.getBinding().setProperty("c", container)
        script.run()

        assertThat(container.run, equalTo(['run']))
        assertThat(container.main, equalTo(['main', 1]))
    }
}

class DynamicOwner {
    Map values = [:]

    def ownerMethod(String value) {
        return value
    }
    
    def getOwnerProp() {
        return 'ownerProp'
    }

    def propertyMissing(String name) {
        if (name == 'dynamicProp') {
            return values[name]
        }
        throw new MissingPropertyException("fail")
    }

    def propertyMissing(String name, Object value) {
        if (name == 'dynamicProp') {
            values[name] = value
            return
        }
        throw new MissingPropertyException("fail")
    }

    def methodMissing(String name, Object params) {
        if (name == 'dynamicMethod') {
            return 'dynamicMethod'
        }
        throw new groovy.lang.MissingMethodException(name, getClass(), params)
    }

    def configure(def container) {
        container.configure {
            list1 {
                // owner properties and methods - owner is a DynamicOwner
                dynamicProp = 'dynamicProp'
                add dynamicProp
                add ownerProp
                add ownerMethod('ownerMethod')
                add dynamicMethod('a', 'b', 'c')
                add dynamicMethod { doesntGetEvaluated }
                // delegate properties and methods - delegate is a TestObject
                add owner.size()
                prop = 'prop'
                add prop
                testObjectDynamicMethod { doesntGetEvaluated }
            }
            list2 {
                add list1
            }
        }
    }
}

class TestObject extends ArrayList<String> {
    def String prop
    String name
    def methodMissing(String name, Object params) {
        if (name == 'testObjectDynamicMethod') {
            add(name)
            return name
        }
        throw new groovy.lang.MissingMethodException(name, getClass(), params)
    }
}
