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

class ResourceVersionListerTest extends Specification {

    def repo = Mock(ExternalResourceRepository)
    def artifact = Mock(Artifact)
    def moduleRevisionId = ModuleRevisionId.newInstance("org.acme", "proj1", "1.0")

    def ResourceVersionLister lister;

    def setup() {
        repo.getFileSeparator() >> "/"
        lister = new ResourceVersionLister(repo)
    }

    def "getVersionList returns propagates Exceptions as ResourceException"() {
        setup:
        def testPattern = "/a/pattern/with/[revision]/"
        1 * repo.list(_) >> { throw new IOException("Test IO Exception") }
        repo.standardize(testPattern) >> testPattern
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

    def "getVersionList returns empty VersionList when repository contains empty list"() {
        setup:
        1 * repo.list(_) >> []
        1 * repo.standardize("/some/[revision]") >> "/some/[revision]"
        expect:
        lister.getVersionList(moduleRevisionId, "/some/[revision]", artifact).empty
    }

    @Unroll
    def "getVersionList resolves versions from from pattern with '#testPattern'"() {
        setup:
        1 * repo.list(repoListingPath) >> repoResult
        1 * repo.standardize(testPattern) >> testPattern
        when:
        def versionList = lister.getVersionList(moduleRevisionId, testPattern, artifact)
        then:
        versionList.versionStrings == ["1", "2.1", "a-version"]
        where:
        testPattern                              | repoListingPath | repoResult
        "[revision]"                             | ""              | ["1", "2.1/", "a-version"]
        "[revision]/"                            | ""              | ["1", "2.1/", "a-version"]
        "/[revision]"                            | "/"             | ["1", "2.1/", "a-version"]
        "/[revision]/"                           | "/"             | ["1", "2.1/", "a-version"]
        "/some/[revision]"                       | "/some/"        | ["/some/1", "/some/2.1/", "/some/a-version"]
        "/some/[revision]/"                      | "/some/"        | ["/some/1", "/some/2.1/", "/some/a-version"]
        "/some/[revision]/lib"                   | "/some/"        | ["/some/1/", "/some/2.1", "/some/a-version"]
        "/some/version-[revision]/lib"           | "/some"         | ["/some/version-1", "/some/version-2.1", "/some/version-a-version", "/some/nonmatching"]
        "/some/version-[revision]/lib/"          | "/some"         | ["/some/version-1", "/some/version-2.1", "/some/version-a-version", "/some/nonmatching"]
        "/some/[revision]-version"               | "/some"         | ["/some/1-version", "/some/2.1-version", "/some/a-version-version", "/some/nonmatching"]
        "/some/[revision]-version/lib"           | "/some"         | ["/some/1-version", "/some/2.1-version", "/some/a-version-version", "/some/nonmatching"]
        "/some/any-[revision]-version/lib"       | "/some"         | ["/some/any-1-version", "/some/any-2.1-version", "/some/any-a-version-version", "/some/nonmatching"]
        "/some/any-[revision]-version/lib/"      | "/some"         | ["/some/any-1-version", "/some/any-2.1-version", "/some/any-a-version-version", "/some/nonmatching"]
        "/some/[revision]/lib/myjar-[revision]/" | "/some/"        | ["/some/1", "/some/2.1", "/some/a-version"]
        "/some/proj-[revision]/[revision]/lib/"  | "/some"         | ["/some/proj-1", "/some/proj-2.1", "/some/proj-a-version"]
    }

    def "getVersionList substitutes non revision placeholders from pattern before hitting repository"() {
        setup:
        1 * repo.standardize(partiallyResolvedPattern) >> partiallyResolvedPattern
        when:
        lister.getVersionList(moduleRevisionId, inputPattern, artifact)
        then:
        1 * repo.list(repoPath) >> []
        where:
        inputPattern                          | partiallyResolvedPattern     | repoPath
        "/[organisation]/[revision]"          | "/org.acme/[revision]"       | "/org.acme/"
        "/[organization]/[revision]"          | "/org.acme/[revision]"       | "/org.acme/"
        "/[module]/[revision]"                | "/proj1/[revision]"          | "/proj1/"
        "/[module]/[revision]-lib.[ext]"      | "/proj1/[revision]-lib.jar"  | "/proj1"
        "/[organisation]/[module]/[revision]" | "/org.acme/proj1/[revision]" | "/org.acme/proj1/"
    }

    def "getVersionList returns empty version list when pattern has no revision token"() {
        setup:
        repo.list(_) >> repoResult
        repo.standardize(testPattern) >> testPattern
        when:
        def versionList = lister.getVersionList(moduleRevisionId, testPattern, artifact)
        then:
        versionList.versionStrings.empty
        where:
        testPattern                      | repoResult
        "/some/pattern/with/no/revision" | ["/some/1-version", "/some/2.1-version", "/some/a-version-version"]
    }
}
