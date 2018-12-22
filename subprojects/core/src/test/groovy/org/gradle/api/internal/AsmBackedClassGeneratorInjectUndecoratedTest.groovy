/*
 * Copyright 2018 the original author or authors.
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


import org.gradle.api.plugins.ExtensionAware
import org.gradle.internal.service.ServiceRegistry

import javax.inject.Inject

class AsmBackedClassGeneratorInjectUndecoratedTest extends AbstractClassGeneratorSpec {
    final ClassGenerator generator = AsmBackedClassGenerator.injectOnly()

    def "returns original class when class is not abstract and no service getter methods present"() {
        expect:
        generator.generate(Bean) == Bean
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
        def bean = create(AsmBackedClassGeneratorTest.AbstractBean, "a")
        bean.a == "a"

        bean instanceof GeneratedSubclass
        !(bean instanceof DynamicObjectAware)
        !(bean instanceof ExtensionAware)
        !(bean instanceof HasConvention)
        !(bean instanceof IConventionAware)
        !(bean instanceof GroovyObject)
    }

    def "generates subclass when service getter methods present"() {
        def services = Stub(ServiceRegistry)
        services.get(Number) >> 12

        expect:
        def bean = create(AsmBackedClassGeneratorTest.BeanWithServiceGetters, services)
        bean.someValue == 12
        bean.calculated == "[12]"

        bean instanceof GeneratedSubclass
        !(bean instanceof DynamicObjectAware)
        !(bean instanceof ExtensionAware)
        !(bean instanceof HasConvention)
        !(bean instanceof IConventionAware)
        !(bean instanceof GroovyObject)
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
