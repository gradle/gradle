/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.plugin.software.internal

import org.gradle.api.internal.DynamicObjectAware
import org.gradle.api.internal.plugins.TargetTypeInformation
import org.gradle.api.model.ObjectFactory
import org.gradle.util.TestUtil
import spock.lang.Specification

class SoftwareFeaturesDynamicObjectTest extends Specification {
    def softwareTypeRegistry = Mock(SoftwareFeatureRegistry)
    def softwareFeatureApplicator = Mock(SoftwareFeatureApplicator)
    def dynamicObjectAware = Mock(DynamicObjectAware)
    def softwareTypeImplementation = Mock(SoftwareFeatureImplementation) {
        it.getTargetDefinitionType() >> new TargetTypeInformation.DefinitionTargetTypeInformation(Object.class)
    }
    def context = Mock(SoftwareFeatureSupportInternal.ProjectFeatureDefinitionContext)
    def softwareFeaturesDynamicObject

    def setup() {
        def services = TestUtil.createTestServices {
            it.add(SoftwareFeatureRegistry, softwareTypeRegistry)
            it.add(SoftwareFeatureApplicator, softwareFeatureApplicator)
        }
        softwareFeaturesDynamicObject = services.get(ObjectFactory.class).newInstance(SoftwareFeaturesDynamicObject, dynamicObjectAware, context)
    }

    def "applies software feature when configured"() {
        def foo = new Foo()

        when:
        softwareFeaturesDynamicObject.invokeMethod("foo", closureArg { bar = 'baz' })

        then:
        _ * softwareTypeRegistry.getSoftwareFeatureImplementations() >> ["foo": softwareTypeImplementation]
        1 * softwareFeatureApplicator.applyFeatureTo(dynamicObjectAware, softwareTypeImplementation) >> foo

        and:
        foo.bar == 'baz'
    }

    def "does not apply software feature when property is referenced"() {
        when:
        softwareFeaturesDynamicObject.foo

        then:
        0 * softwareTypeRegistry.getSoftwareFeatureImplementations()
        0 * softwareFeatureApplicator.applyFeatureTo(_, _)

        and:
        thrown(MissingPropertyException)
    }

    def "does not apply software feature when non-conforming method is called"() {
        when:
        softwareFeaturesDynamicObject.invokeMethod("foo", ["bar"] as Object[])

        then:
        0 * softwareTypeRegistry.getSoftwareFeatureImplementations()
        0 * softwareFeatureApplicator.applyFeatureTo(_, _)

        and:
        thrown(MissingMethodException)
    }

    def "missing method when non-existent software type is referenced"() {
        when:
        softwareFeaturesDynamicObject.invokeMethod("fizz", closureArg { bar = 'baz' })

        then:
        _ * softwareTypeRegistry.getSoftwareFeatureImplementations() >> ["foo": softwareTypeImplementation]
        0 * softwareFeatureApplicator.applyFeatureTo(_, _)

        and:
        thrown(MissingMethodException)
    }

    def "does not apply software feature when method is only queried"() {
        when:
        assert softwareFeaturesDynamicObject.hasMethod("foo", closureArg {})

        then:
        _ * softwareTypeRegistry.getSoftwareFeatureImplementations() >> ["foo": softwareTypeImplementation]
        0 * softwareFeatureApplicator.applyFeatureTo(_, _)
    }

    private static Object[] closureArg(Closure closure) {
        return [closure]
    }

    class Foo {
        String bar
    }
}
