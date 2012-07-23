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

import spock.lang.Specification
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.module.descriptor.Artifact

class ChainedVersionListerTest extends Specification {

    VersionLister lister1 = Mock(VersionLister)
    VersionLister lister2 = Mock(VersionLister)

    VersionList versionList1 = Mock(VersionList)
    VersionList versionList2 = Mock(VersionList)

    def setup(){
        lister1.getVersionList(_, _, _) >> versionList1
        lister2.getVersionList(_, _, _) >> versionList2

        versionList1.versionStrings >> ["1.0", "1.2"]
        versionList2.versionStrings >> ["2.2", "2.3"]
    }

    def "assembles versionlist from multiple versionlister when stopOnSuccess = false"(){
        given:
        def chainedVersionLister = new ChainedVersionLister(false)
        chainedVersionLister.appendVersionLister(lister1)
        chainedVersionLister.appendVersionLister(lister2)
        when:
        VersionList version = chainedVersionLister.getVersionList(Mock(ModuleRevisionId), "testPattern", Mock(Artifact));
        then:
        1 * lister1.getVersionList(_, _, _)>> versionList1
        1 * lister2.getVersionList(_, _, _)>> versionList2
        _ * versionList1.isEmpty() >> false;
        _ * versionList2.isEmpty() >> false;
        version.versionStrings as Set == ["1.0", "1.2", "2.2", "2.3"] as Set
    }

    def "stops listing after first success when stopOnSuccess = true"(){
        given:
        def chainedVersionLister = new ChainedVersionLister(true)
        chainedVersionLister.appendVersionLister(lister1)
        chainedVersionLister.appendVersionLister(lister2)
        when:
        VersionList version = chainedVersionLister.getVersionList(Mock(ModuleRevisionId), "testPattern", Mock(Artifact));
        then:
        1 * lister1.getVersionList(_, _, _)>> versionList1
        0 * lister2.getVersionList(_, _, _)>> versionList2
        version.versionStrings as Set == ["1.0", "1.2"] as Set
    }
}
