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

import org.gradle.api.internal.DynamicObjectAware
import org.gradle.api.internal.GeneratedSubclass
import org.gradle.api.internal.HasConvention
import org.gradle.api.internal.IConventionAware
import org.gradle.api.plugins.ExtensionAware
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.instantiation.PropertyRoleAnnotationHandler
import org.gradle.internal.service.ServiceLookup
import org.gradle.internal.state.ModelObject
import org.gradle.internal.state.OwnerAware

import javax.inject.Inject

import static AsmBackedClassGeneratorTest.AbstractBean
import static AsmBackedClassGeneratorTest.BeanWithServiceGetters

class AsmBackedClassGeneratorInjectUndecoratedTest extends AbstractClassGeneratorSpec {
    ClassGenerator generator = AsmBackedClassGenerator.injectOnly([], Stub(PropertyRoleAnnotationHandler), [], new TestCrossBuildInMemoryCacheFactory(), 0)

    def "returns original class when class is not abstract and no service getter methods present"() {
        expect:
        generator.generate(Bean).generatedClass == Bean
    }

    def "can create instance of final class when a subclass is not required"() {
        expect:
        create(FinalBean) != null
    }

    def "can create instance of private class when a subclass is not required"() {
        expect:
        create(PrivateBean) != null
    }

    def "generates subclass that is not decorated when class is abstract"() {
        expect:
        def bean = create(AbstractBean, "a")
        bean.a == "a"

        bean instanceof GeneratedSubclass
        bean.publicType() == AbstractBean

        bean instanceof ModelObject
        bean.modelIdentityDisplayName == null
        !bean.hasUsefulDisplayName()

        bean instanceof OwnerAware

        !(bean instanceof DynamicObjectAware)
        !(bean instanceof ExtensionAware)
        !(bean instanceof HasConvention)
        !(bean instanceof IConventionAware)
        !(bean instanceof GroovyObject)
    }

    def "generates subclass that is not decorated when service getter methods present"() {
        def services = Stub(ServiceLookup)
        services.get(Number) >> 12

        expect:
        // Use a Java class to verify GroovyObject is not mixed in
        def bean = create(BeanWithServiceGetters, services)
        bean.someValue == 12
        bean.calculated == "[12]"

        bean instanceof GeneratedSubclass
        bean.publicType() == BeanWithServiceGetters

        bean instanceof ModelObject
        bean.modelIdentityDisplayName == null
        !bean.hasUsefulDisplayName()

        bean instanceof OwnerAware

        !(bean instanceof DynamicObjectAware)
        !(bean instanceof ExtensionAware)
        !(bean instanceof HasConvention)
        !(bean instanceof IConventionAware)
        !(bean instanceof GroovyObject)
    }

    def "can create decorated and undecorated subclasses of same class"() {
        def services = Stub(ServiceLookup)
        services.get(Number) >> 12

        expect:
        def decorated = create(AsmBackedClassGenerator.decorateAndInject([], Stub(PropertyRoleAnnotationHandler), [], new TestCrossBuildInMemoryCacheFactory(), 0), BeanWithServiceGetters)
        def undecorated = create(AsmBackedClassGenerator.injectOnly([], Stub(PropertyRoleAnnotationHandler), [], new TestCrossBuildInMemoryCacheFactory(),0), BeanWithServiceGetters)
        decorated.class != undecorated.class
        decorated instanceof ExtensionAware
        !(undecorated instanceof ExtensionAware)
    }

    static class Bean {
        @Inject
        Bean(String a, String b) {
        }
    }

    static final class FinalBean {
    }

    private static final class PrivateBean {
    }
}
