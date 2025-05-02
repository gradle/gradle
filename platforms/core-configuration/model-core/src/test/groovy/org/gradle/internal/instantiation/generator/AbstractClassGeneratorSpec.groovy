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

import javax.annotation.Nullable

abstract class AbstractClassGeneratorSpec extends Specification {
    @ClassRule
    @Shared
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    abstract ClassGenerator getGenerator()

    protected <T> T createForSerialization(Class<T> clazz) {
        def nested = Stub(InstanceGenerator)
        _ * nested.newInstanceWithDisplayName(_, _, _) >> { type, name, params -> create(type) }
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
        _ * services.find(InstantiatorFactory.class) >> { TestUtil.instantiatorFactory() }
        _ * services.get(InstantiatorFactory.class) >> { TestUtil.instantiatorFactory() }
        _ * services.find(ObjectFactory.class) >> { TestUtil.objectFactory(tmpDir.testDirectory) }
        _ * services.get(ObjectFactory.class) >> { TestUtil.objectFactory(tmpDir.testDirectory) }
        return services
    }

    protected <T> T doCreate(ClassGenerator generator, Class<T> clazz, ServiceLookup services, @Nullable Describable displayName, Object[] args) {
        generator.generate(clazz)
        def instantiator = new DependencyInjectingInstantiator(new ParamsMatchingConstructorSelector(generator), services)
        if (displayName == null) {
            return instantiator.newInstance(clazz, args)
        } else {
            return instantiator.newInstanceWithDisplayName(clazz, displayName, args)
        }
    }


    static class ManyConstructors {
        // the exact order of these constructors as they are returned by the JDK is undefined
        ManyConstructors(int p0, int p1, int p2, int p3, int p4) {}
        ManyConstructors(int p0, int p1, int p2, int p3, int p4, int p5, int p6, int p7, int p8, int p9) {}
        ManyConstructors(int p0, int p1, int p2) {}
        ManyConstructors() {}
        ManyConstructors(int p0, int p1, int p2, int p3, int p4, int p5, int p6, boolean p7) {}
        ManyConstructors(int p0, int p1) {}
        ManyConstructors(int p0, int p1, int p2, int p3, int p4, int p5, int p6) {}
        ManyConstructors(int p0, int p1, int p2, int p3, int p4, int p5) {}
        ManyConstructors(int p0, int p1, int p2, int p3, int p4, int p5, int p6, int p7, int p8) {}
        ManyConstructors(int p0, int p1, boolean p2) {}
        ManyConstructors(int p0, int p1, int p2, int p3, int p4, int p5, int p6, int p7) {}
        ManyConstructors(int p0, int p1, int p2, int p3) {}
        ManyConstructors(int p0) {}
    }

    def "order of constructors is based on number of parameters"() {
        // We rely on processing the constructors in a predictable order (fewest parameters -> most)
        def constructors = generator.generate(ManyConstructors).getConstructors()
        expect:
        int count = 0
        constructors.each { constructor ->
            assert count <= constructor.parameterTypes.length
            count = constructor.parameterTypes.length
        }
    }
}
