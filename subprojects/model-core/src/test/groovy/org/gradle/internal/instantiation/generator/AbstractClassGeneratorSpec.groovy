/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.instantiation.generator

import org.gradle.api.Describable
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.instantiation.InstanceGenerator
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.service.ServiceLookup
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.ClassRule
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractClassGeneratorSpec extends Specification {
    @ClassRule
    @Shared
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    abstract ClassGenerator getGenerator()

    protected <T> T createForSerialization(Class<T> clazz) {
        def nested = Stub(InstanceGenerator)
        _ * nested.newInstance(_, _) >> { type, params -> create(type) }
        return generator.generate(clazz).getSerializationConstructor(Object).newInstance(defaultServices(), nested)
    }

    protected <T> T create(Class<T> clazz, Object... args) {
        return doCreate(generator, clazz, defaultServices(), null, args)
    }

    protected <T> T create(Class<T> clazz, Describable displayName, Object... args) {
        return doCreate(generator, clazz, defaultServices(), displayName, args)
    }

    protected <T> T create(Class<T> clazz, ServiceLookup services, Object... args) {
        return doCreate(generator, clazz, services, null, args)
    }

    protected <T> T create(ClassGenerator generator, Class<T> clazz, Object... args) {
        return doCreate(generator, clazz, defaultServices(), null, args)
    }

    protected <T> T create(ClassGenerator generator, Class<T> clazz, ServiceLookup services, Object ... args) {
        return doCreate(generator, clazz, services, null, args)
    }

    ServiceLookup defaultServices() {
        ServiceLookup services = Mock(ServiceLookup)
        _ * services.get(InstantiatorFactory.class) >> { TestUtil.instantiatorFactory() }
        _ * services.get(ObjectFactory.class) >> { TestUtil.objectFactory(tmpDir.testDirectory) }
        return services
    }

    protected <T> T doCreate(ClassGenerator generator, Class<T> clazz, ServiceLookup services, Describable displayName, Object[] args) {
        def type = generator.generate(clazz)
        def instantiator = Stub(InstanceGenerator) {
            _ * newInstance(_, _) >> { Class<Object> nested, Object[] nestedArgs ->
                return doCreate(generator, nested, services, (Describable) null, nestedArgs)
            }
            _ * newInstanceWithDisplayName(_, _, _) >> { Class<Object> nested, Describable name, Object[] nestedArgs ->
                return doCreate(generator, nested, services, name, nestedArgs)
            }
        }
        return type.constructors[0].newInstance(services, instantiator, displayName, args)
    }
}
