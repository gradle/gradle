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
import org.gradle.util.internal.TextUtil
import spock.lang.Specification


class ProjectLayoutSetupRegistrySpec extends Specification {
    def defaultType = descriptor("default")
    def converter = converter("maven")
    def registry = new ProjectLayoutSetupRegistry(defaultType, converter, Mock(TemplateOperationFactory))

    def "can add multiple descriptors"() {
        when:
        registry.add(descriptor("desc1"))
        registry.add(descriptor("desc2"))
        registry.add(descriptor("desc3"))

        then:
        registry.get("desc1") != null
        registry.get("desc2") != null
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

    def "getAllTypes lists all registered types"() {
        setup:
        registry.add(descriptor("desc1"))
        registry.add(descriptor("desc2"))
        registry.add(descriptor("desc3"))

        expect:
        registry.getAllTypes() == ["default", "desc1", "desc2", "desc3", "maven"]
    }

    def "lookup fails for unknown type"() {
        setup:
        registry.add(descriptor("desc1"))
        registry.add(descriptor("desc2"))
        registry.add(descriptor("desc3"))

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

    def descriptor(String id) {
        def descriptor = Mock(BuildInitializer)
        descriptor.id >> id
        return descriptor
    }

    def converter(String id) {
        def descriptor = Mock(BuildConverter)
        descriptor.id >> id
        return descriptor
    }
}
