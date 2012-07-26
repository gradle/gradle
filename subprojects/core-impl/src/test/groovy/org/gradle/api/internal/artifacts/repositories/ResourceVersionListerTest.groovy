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
import spock.lang.Specification
import org.gradle.api.internal.resource.ResourceNotFoundException

class ResourceVersionListerTest extends Specification {

    def repo = Mock(ExternalResourceRepository)
    def artifact = Mock(Artifact)
    def moduleRevisionId = ModuleRevisionId.newInstance("org.acme", "testproject", "1.0")

    def ResourceVersionLister lister;

    def setup() {
        1 * repo.getFileSeparator() >> "/"
        lister = new ResourceVersionLister(repo)
    }

    def "getVersionList returns propagates Exceptions as ResourceException"() {
        setup:
        def testPattern = "/a/pattern/with/[revision]/"
        1 * repo.list(_) >> { throw new IOException("Test IO Exception") }
        1 * repo.standardize(testPattern) >> testPattern
        when:
        lister.getVersionList(moduleRevisionId, testPattern, artifact)
        then:
        thrown(org.gradle.api.internal.resource.ResourceException)
    }

    def "getVersionList throws ResourceNotFoundException for missing resource"() {
        setup:
        1 * repo.list(_) >> null
        1 * repo.standardize(testPattern) >> testPattern
        when:
        lister.getVersionList(moduleRevisionId, testPattern, artifact)
        then:
        thrown(ResourceNotFoundException)
        where:
        testPattern << ["/some/[revision]", "/some/version-[revision]"]
    }

    def "getVersionList resolves versions from pattern with version as directory name"() {
        setup:
        1 * repo.list("/some/") >> repoResult
        1 * repo.standardize(testPattern) >> testPattern
        when:
        def versionList = lister.getVersionList(moduleRevisionId, testPattern, artifact)
        then:
        versionList.versionStrings == ["1.5", "1.6", "another-version"]
        where:
        testPattern             | repoResult
        "/some/[revision]"      | ["/some/1.5", "/some/1.6/", "/some/another-version"]
        "/some/[revision]/"     | ["/some/1.5", "/some/1.6/", "/some/another-version"]
        "/some/[revision]/lib"  | ["/some/1.5/", "/some/1.6", "/some/another-version"]
    }

    def "getVersionList resolves versions from pattern with custom version directory name"() {
        setup:
        repo.list(_) >> [repoResult]
        repo.standardize(testPattern) >> testPattern
        when:
        def calculatedVersionList = lister.getVersionList(moduleRevisionId, testPattern, artifact)
        then:
        calculatedVersionList.versionStrings == expectedList
        where:
        testPattern                         | repoResult              || expectedList
        "/some/version-[revision]/lib"      | "/some/version-1.1"     || ["1.1"]
        "/some/version-[revision]/lib/"     | "/some/version-1.5"     || ["1.5"]
        "/some/[revision]-version"          | "/some/1.2-version"     || ["1.2"]
        "[revision]-version/lib"            | "/1.1.1-version"        || ["1.1.1"]
        "/some/any-[revision]-version/lib"  | "/some/any-1.5-version" || ["1.5"]
        "/some/any-[revision]-version/lib"  | "/some/any-nonmatching" || []
        "/some/not-matching/lib"            | "/some/any-nonmatching" || []
    }
}
