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

package org.gradle.buildinit.plugins.internal

import org.gradle.api.GradleException
import spock.lang.Specification


class ProjectLayoutSetupRegistrySpec extends Specification {


    ProjectLayoutSetupRegistry registry = new ProjectLayoutSetupRegistry()

    def "can add multiple projectlayoutdescriptors"() {
        when:
        registry.add("desc1", Mock(ProjectInitDescriptor))
        registry.add("desc2", Mock(ProjectInitDescriptor))
        registry.add("desc3", Mock(ProjectInitDescriptor))
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
        registry.add("desc1", Mock(ProjectInitDescriptor))
        registry.add("desc1", Mock(ProjectInitDescriptor))
        then:
        def e = thrown(GradleException)
        e.message == "ProjectDescriptor with ID 'desc1' already registered."
    }

    def "getSupportedTypes lists all registered types"() {
        setup:
        registry.add("desc1", Mock(ProjectInitDescriptor))
        registry.add("desc2", Mock(ProjectInitDescriptor))
        registry.add("desc3", Mock(ProjectInitDescriptor))
        expect:
        registry.getSupportedTypes() == ["desc1", "desc2", "desc3"]
    }
}
