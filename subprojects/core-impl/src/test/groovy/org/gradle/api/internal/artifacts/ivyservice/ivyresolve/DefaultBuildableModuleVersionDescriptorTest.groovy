/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException
import spock.lang.Specification
import org.apache.ivy.core.module.descriptor.ModuleDescriptor

class DefaultBuildableModuleVersionDescriptorTest extends Specification {
    final DefaultBuildableModuleVersionDescriptor descriptor = new DefaultBuildableModuleVersionDescriptor()

    def "has unknown state by default"() {
        expect:
        descriptor.state == BuildableModuleVersionDescriptor.State.Unknown
    }

    def "can mark as missing"() {
        when:
        descriptor.missing()

        then:
        descriptor.state == BuildableModuleVersionDescriptor.State.Missing
        descriptor.failure == null
    }

    def "can mark as failed"() {
        def failure = new ModuleVersionResolveException("broken")

        when:
        descriptor.failed(failure)

        then:
        descriptor.state == BuildableModuleVersionDescriptor.State.Failed
        descriptor.failure == failure
    }

    def "can mark as resolved"() {
        def moduleDescriptor = Mock(ModuleDescriptor)

        when:
        descriptor.resolved(moduleDescriptor, true)

        then:
        descriptor.state == BuildableModuleVersionDescriptor.State.Resolved
        descriptor.failure == null
        descriptor.descriptor == moduleDescriptor
        descriptor.changing
    }

    def "cannot get result when not resolved"() {
        when:
        descriptor.descriptor

        then:
        thrown(IllegalStateException)

        when:
        descriptor.failure

        then:
        thrown(IllegalStateException)
    }

    def "cannot get result when failed"() {
        given:
        def failure = new ModuleVersionResolveException("broken")
        descriptor.failed(failure)

        when:
        descriptor.descriptor

        then:
        ModuleVersionResolveException e = thrown()
        e == failure
    }

    def "cannot get result when missing"() {
        given:
        descriptor.missing()

        when:
        descriptor.descriptor

        then:
        thrown(IllegalStateException)
    }
}
