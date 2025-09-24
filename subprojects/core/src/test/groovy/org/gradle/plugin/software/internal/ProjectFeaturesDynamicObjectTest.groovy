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

class ProjectFeaturesDynamicObjectTest extends Specification {
    def projectFeatureRegistry = Mock(ProjectFeatureRegistry)
    def projectFeatureApplicator = Mock(ProjectFeatureApplicator)
    def dynamicObjectAware = Mock(DynamicObjectAware)
    def projectTypeImplementation = Mock(ProjectFeatureImplementation) {
        it.getTargetDefinitionType() >> new TargetTypeInformation.DefinitionTargetTypeInformation(Object.class)
    }
    def context = Mock(ProjectFeatureSupportInternal.ProjectFeatureDefinitionContext)
    def projectFeaturesDynamicObject

    def setup() {
        def services = TestUtil.createTestServices {
            it.add(ProjectFeatureRegistry, projectFeatureRegistry)
            it.add(ProjectFeatureApplicator, projectFeatureApplicator)
        }
        projectFeaturesDynamicObject = services.get(ObjectFactory.class).newInstance(ProjectFeaturesDynamicObject, dynamicObjectAware, context)
    }

    def "applies project feature when configured"() {
        def foo = new Foo()

        when:
        projectFeaturesDynamicObject.invokeMethod("foo", closureArg { bar = 'baz' })

        then:
        _ * projectFeatureRegistry.getProjectFeatureImplementations() >> ["foo": projectTypeImplementation]
        1 * projectFeatureApplicator.applyFeatureTo(dynamicObjectAware, projectTypeImplementation) >> foo

        and:
        foo.bar == 'baz'
    }

    def "does not apply project feature when property is referenced"() {
        when:
        projectFeaturesDynamicObject.foo

        then:
        0 * projectFeatureRegistry.getProjectFeatureImplementations()
        0 * projectFeatureApplicator.applyFeatureTo(_, _)

        and:
        thrown(MissingPropertyException)
    }

    def "does not apply project feature when non-conforming method is called"() {
        when:
        projectFeaturesDynamicObject.invokeMethod("foo", ["bar"] as Object[])

        then:
        0 * projectFeatureRegistry.getProjectFeatureImplementations()
        0 * projectFeatureApplicator.applyFeatureTo(_, _)

        and:
        thrown(MissingMethodException)
    }

    def "missing method when non-existent project type is referenced"() {
        when:
        projectFeaturesDynamicObject.invokeMethod("fizz", closureArg { bar = 'baz' })

        then:
        _ * projectFeatureRegistry.getProjectFeatureImplementations() >> ["foo": projectTypeImplementation]
        0 * projectFeatureApplicator.applyFeatureTo(_, _)

        and:
        thrown(MissingMethodException)
    }

    def "does not apply project feature when method is only queried"() {
        when:
        assert projectFeaturesDynamicObject.hasMethod("foo", closureArg {})

        then:
        _ * projectFeatureRegistry.getProjectFeatureImplementations() >> ["foo": projectTypeImplementation]
        0 * projectFeatureApplicator.applyFeatureTo(_, _)
    }

    private static Object[] closureArg(Closure closure) {
        return [closure]
    }

    class Foo {
        String bar
    }
}
