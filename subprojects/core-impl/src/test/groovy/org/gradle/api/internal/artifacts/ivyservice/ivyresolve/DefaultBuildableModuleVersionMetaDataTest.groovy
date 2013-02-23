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

import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class DefaultBuildableModuleVersionMetaDataTest extends Specification {
    final DefaultBuildableModuleVersionMetaData descriptor = new DefaultBuildableModuleVersionMetaData()
    ModuleSource moduleSource = Mock()

    def "has unknown state by default"() {
        expect:
        descriptor.state == BuildableModuleVersionMetaData.State.Unknown
    }

    def "can mark as missing"() {
        when:
        descriptor.missing()

        then:
        descriptor.state == BuildableModuleVersionMetaData.State.Missing
        descriptor.failure == null
    }

    def "can mark as probably missing"() {
        when:
        descriptor.probablyMissing()

        then:
        descriptor.state == BuildableModuleVersionMetaData.State.ProbablyMissing
        descriptor.failure == null
    }

    def "can mark as failed"() {
        def failure = new ModuleVersionResolveException(newSelector("a", "b", "c"), "broken")

        when:
        descriptor.failed(failure)

        then:
        descriptor.state == BuildableModuleVersionMetaData.State.Failed
        descriptor.failure == failure
    }

    def "can mark as resolved"() {
        def id = Mock(ModuleVersionIdentifier)
        def moduleDescriptor = Mock(ModuleDescriptor)

        when:
        descriptor.resolved(id, moduleDescriptor, true, moduleSource)

        then:
        descriptor.state == BuildableModuleVersionMetaData.State.Resolved
        descriptor.failure == null
        descriptor.id == id
        descriptor.descriptor == moduleDescriptor
        descriptor.changing
        descriptor.moduleSource == moduleSource
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
        def failure = new ModuleVersionResolveException(newSelector("a", "b", "c"), "broken")
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

    def "cannot get result when probably missing"() {
        given:
        descriptor.probablyMissing()

        when:
        descriptor.descriptor

        then:
        thrown(IllegalStateException)
    }

    def "cannot get module source when failed"() {
        given:
        def failure = new ModuleVersionResolveException(newSelector("a", "b", "c"), "broken")
        descriptor.failed(failure)

        when:
        descriptor.getModuleSource()

        then:
        ModuleVersionResolveException e = thrown()
        e == failure
    }

    def "cannot set module source when failed"() {
        given:
        def failure = new ModuleVersionResolveException(newSelector("a", "b", "c"), "broken")
        descriptor.failed(failure)

        when:
        descriptor.setModuleSource(Mock(ModuleSource))

        then:
        ModuleVersionResolveException e = thrown()
        e == failure
    }

    def "cannot get module source when missing"() {
        given:
        descriptor.missing()

        when:
        descriptor.getModuleSource()

        then:
        thrown(IllegalStateException)
    }

    def "cannot set module source when missing"() {
        given:
        descriptor.missing()

        when:
        descriptor.setModuleSource(Mock(ModuleSource))

        then:
        thrown(IllegalStateException)
    }

    def "cannot get module source when probably missing"() {
        given:
        descriptor.probablyMissing()

        when:
        descriptor.getModuleSource()

        then:
        thrown(IllegalStateException)
    }

    def "cannot set module source when probably missing"() {
        given:
        descriptor.probablyMissing()

        when:
        descriptor.setModuleSource(Mock(ModuleSource))

        then:
        thrown(IllegalStateException)
    }
}
