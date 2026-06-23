/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.internal.reflect.Instantiator
import org.gradle.model.internal.core.NamedEntityInstantiator

class DefaultPolymorphicDomainObjectContainerBaseTest extends AbstractNamedDomainObjectContainerTest {
    def setup() {
        container = instantiator.newInstance(PolymorphicTestContainer.class, instantiator, collectionCallbackActionDecorator)
    }
}

class PolymorphicTestContainer extends AbstractPolymorphicDomainObjectContainer<TestObject> {
    PolymorphicTestContainer(Instantiator instantiator, CollectionCallbackActionDecorator collectionCallbackActionDecorator) {
        super(TestObject, instantiator, new DynamicPropertyNamer(), collectionCallbackActionDecorator)
    }

    @Override
    NamedEntityInstantiator<TestObject> getEntityInstantiator() {
        throw new UnsupportedOperationException()
    }

    @Override
    protected TestObject doCreate(String name) {
        def testObject = new TestObject(instantiator)
        testObject.name = name
        testObject
    }

    @Override
    protected <U extends TestObject> U doCreate(String name, Class<U> type) {
        throw new UnsupportedOperationException("doCreate")
    }

    @Override
    Set<? extends Class<? extends TestObject>> getCreateableTypes() {
        Collections.singleton(TestObject)
    }
}
