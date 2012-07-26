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

    def "pattern without revision returns empty list"() {
        setup:
        def testPattern = "/some/pattern/without/rev"
        1 * repo.standardize(testPattern) >> testPattern
        expect:
        lister.getVersionList(moduleRevisionId, testPattern, artifact).versionStrings == []
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
        testPattern             || repoResult
        "/some/[revision]"      || ["/some/1.5", "/some/1.6/", "/some/another-version"]
        "/some/[revision]/"     || ["/some/1.5", "/some/1.6/", "/some/another-version"]
        "/some/[revision]/lib"  || ["/some/1.5/", "/some/1.6", "/some/another-version"]
    }

    def "getVersionList resolves versions from pattern with custom version directory name"() {
        setup:
        1 * repo.list(_) >> [repoResult]
        1 * repo.standardize(testPattern) >> testPattern
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
    }

    def "revisionIsParentDirectoryName checks wether revision token equals parent directory name with"() {
        expect:
        lister.revisionIsParentDirectoryName(pattern) == result
        where:
        pattern                         ||  result
        "[revision]"                    || true
        "[revision]/lib"                || true
        "[revision]/"                   || true
        "/a/pattern/[revision]"         || true
        "/a/pattern/[revision]/"        || true
        "version-[revision]"            || false
        "/a/pattern/version-[revision]" || false
        "/a/pattern/[revision]-version" || false

    }

    def "createRegexPattern creates regex pattern for resolving revision with"() {
        when:
        def pattern = lister.createRegexPattern(inputPattern, slashindex)
        then:
        def matcher = pattern.matcher(value)
        matcher.matches()
        matcher.group(1) == versionValue

        where:
        inputPattern                   | slashindex | value                 || versionValue   //slashindex
        "version-[revision]/lib"       | -1         | "version-1.5"         || "1.5"
        "/some/version-[revision]/lib" | 5          | "/some/version-1.5"   || "1.5"
        "/some/[revision]-version/lib" | 5          | "/some/1.1.0-version" || "1.1.0"
        "/some/version-[revision]/lib" | 5          | "/some/version-1.2.0" || "1.2.0"
        "/some/rev-[revision]-postfix" | 5          | "/some/rev-1-postfix" || "1"
    }

    def "listAll returns extracts version info from resource list loaded from repository"() {
        setup:
        1 * repo.list("/a/path") >> ["/some/path/1.0", "some/path/1.1"]
        when:
        def vList = lister.listAll("/a/path")
        then:
        vList == ["1.0", "1.1"]
    }

    def "listAll throws ResourceNotFoundException when for when resource not available via repository"() {
        setup:
        1 * repo.list("/a/path") >> null
        when:
        lister.listAll("/a/path")
        then:
        thrown(ResourceNotFoundException)
    }

    def "listAll returns returns empty list for empty repository list"() {
        setup:
        1 * repo.list("/a/path") >> []
        when:
        def vList = lister.listAll("/a/path")
        then:
        vList.empty
    }

    def "extractVersionInfoFromPaths returns last path element in path"() {
        expect:
        lister.extractVersionInfoFromPaths(["/some/path/version-1.0"]) == ["version-1.0"]
    }

    def "extractVersionInfoFromPaths ignores trailing slashes in path"() {
        expect:
        lister.extractVersionInfoFromPaths(["/path/with/trailing/slash/version-1.0/"]) == ["version-1.0"]
    }
}
