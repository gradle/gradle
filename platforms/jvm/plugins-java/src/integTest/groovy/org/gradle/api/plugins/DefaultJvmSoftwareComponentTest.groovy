/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal
import org.gradle.jvm.component.internal.DefaultJvmSoftwareComponent
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

/**
 * Tests {@link DefaultJvmSoftwareComponent}.
 */
class DefaultJvmSoftwareComponentTest extends AbstractProjectBuilderSpec {

    def "can create multiple component instances in a single project"() {
        given:
        project.plugins.apply(JavaBasePlugin)

        expect:
        project.objects.newInstance(DefaultJvmSoftwareComponent, "name")
        project.objects.newInstance(DefaultJvmSoftwareComponent, "name2")
        project.objects.newInstance(DefaultJvmSoftwareComponent, "name3")
    }

    def "can add multiple feature instances to the same component"() {
        given:
        project.plugins.apply(JavaBasePlugin)

        def f1 = createFeature("main")
        def f2 = createFeature("foo")
        def f3 = createFeature("bar")

        when:
        def component = project.objects.newInstance(DefaultJvmSoftwareComponent, "name")

        component.features.add(f1)
        component.features.add(f2)
        component.features.add(f3)

        then:
        component.features.containsAll([f1, f2, f3])
    }

    def createFeature(String name) {
        Mock(JvmFeatureInternal) {
            getName() >> name
        }
    }
}
