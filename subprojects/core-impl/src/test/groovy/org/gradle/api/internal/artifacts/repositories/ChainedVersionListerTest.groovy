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

package org.gradle.api.internal.artifacts.repositories

import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.internal.resource.ResourceNotFoundException
import spock.lang.Specification
import spock.lang.Unroll
import org.gradle.api.internal.resource.ResourceException;

class ChainedVersionListerTest extends Specification {

    VersionLister lister1 = Mock()
    VersionLister lister2 = Mock()

    VersionList versionList1 = Mock()
    VersionList versionList2 = Mock()

    Artifact artifact = Mock()
    ModuleRevisionId moduleRevisionId = Mock()

    def chainedVersionLister = new ChainedVersionLister(lister1, lister2)

    def "visit stops listing after first success"() {
        when:
        VersionList versionList = chainedVersionLister.getVersionList(moduleRevisionId);

        then:
        1 * lister1.getVersionList(moduleRevisionId) >> versionList1
        1 * lister2.getVersionList(moduleRevisionId) >> versionList2

        when:
        versionList.visit("testPattern", artifact)

        then:
        1 * versionList1.visit("testPattern", artifact)
        0 * _._

        when:
        def result = versionList.versionStrings

        then:
        result == ["1.0", "1.2"] as Set

        and:
        versionList1.versionStrings >> ["1.0", "1.2"]
        versionList2.versionStrings >> []
    }

    @Unroll
    def "visit ignores #exception.class.simpleName of failed VersionLister"() {
        given:
        lister1.getVersionList(moduleRevisionId) >> versionList1
        lister2.getVersionList(moduleRevisionId) >> versionList2

        VersionList versionList = chainedVersionLister.getVersionList(moduleRevisionId)

        when:
        versionList.visit("testPattern", artifact)

        then:
        1 * versionList1.visit("testPattern", artifact) >> { throw exception }
        1 * versionList2.visit("testPattern", artifact)

        where:
        exception << [new ResourceNotFoundException("test resource not found exception"), new ResourceException("test resource exception"), new RuntimeException("broken")]
    }

    def "visit rethrows ResourceNotFoundException of failed last VersionLister"() {
        given:
        def exception = new ResourceNotFoundException("not found")
        lister1.getVersionList(moduleRevisionId) >> versionList1
        lister2.getVersionList(moduleRevisionId) >> versionList2

        VersionList versionList = chainedVersionLister.getVersionList(moduleRevisionId)

        when:
        versionList.visit("testPattern", artifact)

        then:
        def e = thrown(ResourceNotFoundException)
        e == exception

        and:
        1 * versionList1.visit("testPattern", artifact) >> { throw new ResourceNotFoundException("ignore me") }
        1 * versionList2.visit("testPattern", artifact) >> { throw exception }
    }

    def "visit wraps failed last VersionLister"() {
        given:
        def exception = new RuntimeException("broken")
        lister1.getVersionList(moduleRevisionId) >> versionList1
        lister2.getVersionList(moduleRevisionId) >> versionList2

        VersionList versionList = chainedVersionLister.getVersionList(moduleRevisionId)

        when:
        versionList.visit("testPattern", artifact)

        then:
        def e = thrown(ResourceException)
        e.message == "Failed to list versions for ${moduleRevisionId}."
        e.cause == exception

        and:
        1 * versionList1.visit("testPattern", artifact) >> { throw new ResourceNotFoundException("ignore me") }
        1 * versionList2.visit("testPattern", artifact) >> { throw exception }
    }
}
