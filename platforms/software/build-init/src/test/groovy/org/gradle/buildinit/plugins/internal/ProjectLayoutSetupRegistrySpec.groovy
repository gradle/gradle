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
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType
import org.gradle.util.internal.TextUtil
import spock.lang.Specification


class ProjectLayoutSetupRegistrySpec extends Specification {
    def defaultType = generator("default")
    def converter = converter("maven")

    def "can register multiple descriptors"() {
        when:
        def registry = registry(generator("desc1"), generator("desc2"), generator("desc3"))

        then:
        registry.get("desc1") != null
        registry.get("desc2") != null
        registry.get("desc3") != null
    }

    def "cannot register multiple descriptors with same id"() {
        when:
        registry(generator("desc1"), generator("desc1"))

        then:
        def e = thrown(GradleException)
        e.message == "ProjectDescriptor with ID 'desc1' already registered."
    }

    def "getAllTypes lists all registered types"() {
        setup:
        def registry = registry(generator("desc1"), generator("desc2"), generator("desc3"))

        expect:
        registry.getAllTypes() == ["default", "desc1", "desc2", "desc3", "maven"]
    }

    def "lookup fails for unknown type"() {
        setup:
        def registry = registry(generator("desc1"), generator("desc2"), generator("desc3"))

        when:
        registry.get("unknown")

        then:
        def e = thrown(GradleException)
        e.message == TextUtil.toPlatformLineSeparators("""The requested build type 'unknown' is not supported. Supported types:
  - 'default'
  - 'desc1'
  - 'desc2'
  - 'desc3'
  - 'maven'""")
    }

    def "lists only the component types that have a registered generator, in display order"() {
        setup:
        defaultType = generator("basic", ComponentType.BASIC)
        def registry = registry(generator("desc1", ComponentType.LIBRARY), generator("desc2", ComponentType.APPLICATION), generator("desc3", ComponentType.LIBRARY))

        expect:
        registry.getComponentTypes() == [ComponentType.APPLICATION, ComponentType.LIBRARY, ComponentType.BASIC]
    }

    ProjectLayoutSetupRegistry registry(BuildInitializer... projectTypes) {
        return new ProjectLayoutSetupRegistry(defaultType, converter, Mock(TemplateOperationFactory), projectTypes.toList())
    }

    BuildGenerator generator(String id, ComponentType componentType = ComponentType.BASIC) {
        def descriptor = Mock(BuildGenerator)
        descriptor.id >> id
        descriptor.componentType >> componentType
        return descriptor
    }

    BuildConverter converter(String id) {
        def descriptor = Mock(BuildConverter)
        descriptor.id >> id
        return descriptor
    }
}
