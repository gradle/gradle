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

package org.gradle.api.internal.artifacts.repositories.resolver

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.internal.resolve.result.ResourceAwareResolveResult
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.resource.ResourceException
import org.gradle.internal.resource.ResourceNotFoundException
import spock.lang.Specification

class ChainedVersionListerTest extends Specification {

    VersionLister lister1 = Mock()
    VersionLister lister2 = Mock()

    VersionPatternVisitor versionList1 = Mock()
    VersionPatternVisitor versionList2 = Mock()

    ResourcePattern pattern = Mock()
    ModuleIdentifier module = Mock()
    IvyArtifactName artifact = Mock()
    ResourceAwareResolveResult result = Mock()

    def chainedVersionLister = new ChainedVersionLister(lister1, lister2)

    def "visit stops listing after first success"() {
        def versions = []

        when:
        VersionPatternVisitor versionList = chainedVersionLister.newVisitor(module, versions, result)

        then:
        1 * lister1.newVisitor(module, versions, result) >> versionList1
        1 * lister2.newVisitor(module, versions, result) >> versionList2

        when:
        versionList.visit(pattern, artifact)

        then:
        1 * versionList1.visit(pattern, artifact)
        0 * _._
    }

    def "visit ignores ResourceNotFoundException when another VersionLister provides a result"() {
        def versions = []

        given:
        lister1.newVisitor(module, versions, result) >> versionList1
        lister2.newVisitor(module, versions, result) >> versionList2

        VersionPatternVisitor versionList = chainedVersionLister.newVisitor(module, versions, result)

        when:
        versionList.visit(pattern, artifact)

        then:
        1 * versionList1.visit(pattern, artifact) >> { throw new ResourceNotFoundException(URI.create("scheme:thing"), "ignore me") }
        1 * versionList2.visit(pattern, artifact)
    }

    def "visit fails when VersionLister throws exception"() {
        def versions = []
        def exception = new ResourceException(URI.create("scheme:thing"), "test resource exception")

        given:
        lister1.newVisitor(module, versions, result) >> versionList1
        lister2.newVisitor(module, versions, result) >> versionList2

        VersionPatternVisitor versionList = chainedVersionLister.newVisitor(module, versions, result)

        when:
        versionList.visit(pattern, artifact)

        then:
        def e = thrown(ResourceException)
        e.message == "Failed to list versions for ${module}."
        e.cause == exception

        and:
        1 * versionList1.visit(pattern, artifact) >> { throw exception }
        0 * versionList2._
    }

    def "visit rethrows ResourceNotFoundException of failed first VersionLister"() {
        given:
        def exception = new ResourceNotFoundException(URI.create("scheme:thing"), "not found")
        def versions = []
        lister1.newVisitor(module, versions, result) >> versionList1
        lister2.newVisitor(module, versions, result) >> versionList2

        VersionPatternVisitor versionList = chainedVersionLister.newVisitor(module, versions, result)

        when:
        versionList.visit(pattern, artifact)

        then:
        def e = thrown(ResourceNotFoundException)
        e == exception

        and:
        1 * versionList1.visit(pattern, artifact) >> { throw exception }
        1 * versionList2.visit(pattern, artifact) >> { throw new ResourceNotFoundException(URI.create("scheme:thing"), "ignore me") }
    }

    def "visit wraps failed last VersionLister"() {
        given:
        def versions = []
        def exception = new RuntimeException("broken")
        lister1.newVisitor(module, versions, result) >> versionList1
        lister2.newVisitor(module, versions, result) >> versionList2

        VersionPatternVisitor versionList = chainedVersionLister.newVisitor(module, versions, result)

        when:
        versionList.visit(pattern, artifact)

        then:
        def e = thrown(ResourceException)
        e.message == "Failed to list versions for ${module}."
        e.cause == exception

        and:
        1 * versionList1.visit(pattern, artifact) >> { throw new ResourceNotFoundException(URI.create("scheme:thing"), "ignore me") }
        1 * versionList2.visit(pattern, artifact) >> { throw exception }
    }
}
