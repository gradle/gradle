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

import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class DefaultBuildableModuleVersionMetaDataResolveResultTest extends Specification {
    final DefaultBuildableModuleVersionMetaDataResolveResult descriptor = new DefaultBuildableModuleVersionMetaDataResolveResult()
    ModuleSource moduleSource = Stub()

    def "has unknown state by default"() {
        expect:
        descriptor.state == BuildableModuleVersionMetaDataResolveResult.State.Unknown
    }

    def "can mark as missing"() {
        when:
        descriptor.missing()

        then:
        descriptor.state == BuildableModuleVersionMetaDataResolveResult.State.Missing
        descriptor.failure == null
    }

    def "can mark as probably missing"() {
        when:
        descriptor.probablyMissing()

        then:
        descriptor.state == BuildableModuleVersionMetaDataResolveResult.State.ProbablyMissing
        descriptor.failure == null
    }

    def "can mark as failed"() {
        def failure = new ModuleVersionResolveException(newSelector("a", "b", "c"), "broken")

        when:
        descriptor.failed(failure)

        then:
        descriptor.state == BuildableModuleVersionMetaDataResolveResult.State.Failed
        descriptor.failure == failure
    }

    def "can mark as resolved"() {
        def id = Stub(ModuleVersionIdentifier)
        def moduleDescriptor = Stub(ModuleDescriptor)

        when:
        descriptor.resolved(id, moduleDescriptor, true, moduleSource)

        then:
        descriptor.state == BuildableModuleVersionMetaDataResolveResult.State.Resolved
        descriptor.failure == null
        descriptor.id == id
        descriptor.descriptor == moduleDescriptor
        descriptor.changing
        descriptor.moduleSource == moduleSource
    }

    def "builds and caches the dependency meta-data from the module descriptor"() {
        def id = Stub(ModuleVersionIdentifier)
        def moduleDescriptor = Mock(ModuleDescriptor)
        def dependency1 = new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org", "module", "1.2"), false)
        def dependency2 = new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org", "module", "1.2"), false)

        given:
        moduleDescriptor.dependencies >> ([dependency1, dependency2] as DependencyDescriptor[])

        and:
        descriptor.resolved(id, moduleDescriptor, true, moduleSource)

        when:
        def deps = descriptor.dependencies

        then:
        deps.size() == 2
        deps[0].descriptor == dependency1
        deps[1].descriptor == dependency2

        when:
        def deps2 = descriptor.dependencies

        then:
        deps2.is(deps)

        and:
        0 * moduleDescriptor._
    }

    def "builds artifacts from the module descriptor"() {
        def id = Stub(ModuleVersionIdentifier)
        def moduleDescriptor = Stub(ModuleDescriptor)
        def artifact1 = Stub(Artifact)
        def artifact2 = Stub(Artifact)

        given:
        moduleDescriptor.getArtifacts("config") >> ([artifact1, artifact2] as Artifact[])

        and:
        descriptor.resolved(id, moduleDescriptor, true, moduleSource)

        when:
        def artifacts = descriptor.getArtifacts("config")

        then:
        artifacts.size() == 2
        artifacts[0] == artifact1
        artifacts[1] == artifact2
    }

    def "can replace the dependencies for the module version"() {
        def id = Stub(ModuleVersionIdentifier)
        def moduleDescriptor = Mock(ModuleDescriptor)
        def dependency1 = Stub(DependencyMetaData)
        def dependency2 = Stub(DependencyMetaData)

        given:
        descriptor.resolved(id, moduleDescriptor, true, moduleSource)

        when:
        descriptor.dependencies = [dependency1, dependency2]

        then:
        descriptor.dependencies == [dependency1, dependency2]

        and:
        0 * moduleDescriptor._
    }

    def "cannot get failure when not resolved"() {
        when:
        descriptor.failure

        then:
        thrown(IllegalStateException)
    }

    def "cannot get meta-data when not resolved"() {
        when:
        descriptor.metaData

        then:
        thrown(IllegalStateException)
    }

    def "cannot get meta-data when failed"() {
        given:
        def failure = new ModuleVersionResolveException(newSelector("a", "b", "c"), "broken")
        descriptor.failed(failure)

        when:
        descriptor.metaData

        then:
        ModuleVersionResolveException e = thrown()
        e == failure
    }

    def "cannot get descriptor when not resolved"() {
        when:
        descriptor.descriptor

        then:
        thrown(IllegalStateException)
    }

    def "cannot get descriptor when failed"() {
        given:
        def failure = new ModuleVersionResolveException(newSelector("a", "b", "c"), "broken")
        descriptor.failed(failure)

        when:
        descriptor.descriptor

        then:
        ModuleVersionResolveException e = thrown()
        e == failure
    }

    def "cannot get descriptor when missing"() {
        given:
        descriptor.missing()

        when:
        descriptor.descriptor

        then:
        thrown(IllegalStateException)
    }

    def "cannot get descriptor when probably missing"() {
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
