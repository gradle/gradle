/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildsetup.plugins.internal

import org.gradle.api.GradleException
import spock.lang.Specification


class ProjectLayoutSetupRegistrySpec extends Specification {


    ProjectLayoutSetupRegistry registry = new ProjectLayoutSetupRegistry()

    def "can add multiple projectlayoutdescriptors"() {
        when:
        registry.add(descriptor("desc1"))
        registry.add(descriptor("desc2"))
        registry.add(descriptor("desc3"))
        then:
        registry.supports("desc1")
        registry.get("desc1") != null

        registry.supports("desc2")
        registry.get("desc2") != null

        registry.supports("desc3")
        registry.get("desc3") != null
    }

    def "cannot add multiple descriptors with same id"() {
        when:
        registry.add(descriptor("desc1"))
        registry.add(descriptor("desc1"))
        then:
        def e = thrown(GradleException)
        e.message == "ProjectDescriptor with ID 'desc1' already registered."
    }

    ProjectSetupDescriptor descriptor(String descrName) {
        ProjectSetupDescriptor descriptor = Mock()
        _ * descriptor.id >> descrName
        descriptor
    }
}
