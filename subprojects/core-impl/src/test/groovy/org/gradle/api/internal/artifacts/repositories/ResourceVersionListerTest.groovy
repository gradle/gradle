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
import org.gradle.api.internal.resource.ResourceException
import org.gradle.api.internal.resource.ResourceNotFoundException
import spock.lang.Specification
import spock.lang.Unroll

class ResourceVersionListerTest extends Specification {

    def repo = Mock(ExternalResourceRepository)
    def artifact = Mock(Artifact)
    def moduleRevisionId = ModuleRevisionId.newInstance("org.acme", "proj1", "1.0")

    def ResourceVersionLister lister;

    def setup() {
        lister = new ResourceVersionLister(repo)
    }

    def "visit propagates Exceptions as ResourceException"() {
        setup:
        def failure = new IOException("Test IO Exception")
        def testPattern = pattern("/a/pattern/with/[revision]/")
        1 * repo.list(_) >> { throw failure }

        when:
        def versionList = lister.getVersionList(moduleRevisionId)
        versionList.visit(testPattern, artifact)

        then:
        ResourceException e = thrown()
        e.message == "Could not list versions using pattern '/a/pattern/with/[revision]/'."
        e.cause == failure
    }

    def "visit throws ResourceNotFoundException for missing resource"() {
        setup:
        1 * repo.list(_) >> null

        when:
        def versionList = lister.getVersionList(moduleRevisionId)
        versionList.visit(pattern(testPattern), artifact)

        then:
        ResourceNotFoundException e = thrown()
        e.message == "Cannot list versions from /some/."

        where:
        testPattern << ["/some/[revision]", "/some/version-[revision]"]
    }

    def "visit returns empty VersionList when repository contains empty list"() {
        setup:
        1 * repo.list(_) >> []

        when:
        def versionList = lister.getVersionList(moduleRevisionId)
        versionList.visit(pattern("/some/[revision]"), artifact)

        then:
        versionList.empty
    }

    @Unroll
    def "visit resolves versions from from pattern with '#testPattern'"() {
        when:
        def versionList = lister.getVersionList(moduleRevisionId)
        versionList.visit(pattern(testPattern), artifact)

        then:
        versionList.versionStrings == ["1", "2.1", "a-version"] as Set

        and:
        1 * repo.list(repoListingPath) >> repoResult
        0 * repo._

        where:
        testPattern                              | repoListingPath | repoResult
        "[revision]"                             | ""              | ["1", "2.1/", "a-version"]
        "[revision]/"                            | ""              | ["1", "2.1/", "a-version"]
        "/[revision]"                            | "/"             | ["1", "2.1/", "a-version"]
        "/[revision]/"                           | "/"             | ["1", "2.1/", "a-version"]
        "/some/[revision]"                       | "/some/"        | ["/some/1", "/some/2.1/", "/some/a-version"]
        "/some/[revision]/"                      | "/some/"        | ["/some/1", "/some/2.1/", "/some/a-version"]
        "/some/[revision]/lib"                   | "/some/"        | ["/some/1/", "/some/2.1", "/some/a-version"]
        "/some/version-[revision]"               | "/some/"        | ["/some/version-1", "/some/version-2.1", "/some/version-a-version", "/some/nonmatching"]
        "/some/version-[revision]/lib"           | "/some/"        | ["/some/version-1", "/some/version-2.1", "/some/version-a-version", "/some/nonmatching"]
        "/some/version-[revision]/lib/"          | "/some/"        | ["/some/version-1", "/some/version-2.1", "/some/version-a-version", "/some/nonmatching"]
        "/some/[revision]-version"               | "/some/"        | ["/some/1-version", "/some/2.1-version", "/some/a-version-version", "/some/nonmatching"]
        "/some/[revision]-version/lib"           | "/some/"        | ["/some/1-version", "/some/2.1-version", "/some/a-version-version", "/some/nonmatching"]
        "/some/[revision]-lib.[ext]"             | "/some/"        | ["/some/1-lib.jar", "/some/1-lib.zip", "/some/2.1-lib.jar", "/some/a-version-lib.jar", "/some/nonmatching"]
        "/some/any-[revision]-version/lib"       | "/some/"        | ["/some/any-1-version", "/some/any-2.1-version", "/some/any-a-version-version", "/some/nonmatching"]
        "/some/any-[revision]-version/lib/"      | "/some/"        | ["/some/any-1-version", "/some/any-2.1-version", "/some/any-a-version-version", "/some/nonmatching"]
        "/some/[revision]/lib/myjar-[revision]/" | "/some/"        | ["/some/1", "/some/2.1", "/some/a-version"]
        "/some/proj-[revision]/[revision]/lib/"  | "/some/"        | ["/some/proj-1", "/some/proj-2.1", "/some/proj-a-version"]
    }

    def "visit builds union of versions"() {
        when:
        def versionList = lister.getVersionList(moduleRevisionId)
        versionList.visit(pattern("/[revision]/[artifact]-[revision].[ext]"), artifact)
        versionList.visit(pattern("/[organisation]/[revision]/[artifact]-[revision].[ext]"), artifact)

        then:
        versionList.versionStrings == ["1.2", "1.3", "1.4"] as Set

        and:
        1 * repo.list("/") >> ["1.2", "1.3"]
        1 * repo.list("/org.acme/") >> ["1.3", "1.4"]
        0 * repo._
    }

    def "visit ignores duplicate patterns"() {
        when:
        def versionList = lister.getVersionList(moduleRevisionId)
        versionList.visit(pattern("/a/[revision]/[artifact]-[revision].[ext]"), artifact)
        versionList.visit(pattern("/a/[revision]/[artifact]-[revision]"), artifact)

        then:
        versionList.versionStrings == ["1.2", "1.3"] as Set

        and:
        1 * repo.list("/a/") >> ["1.2", "1.3"]
        0 * repo._
    }

    def "visit substitutes non revision placeholders from pattern before hitting repository"() {
        when:
        def versionList = lister.getVersionList(moduleRevisionId)
        versionList.visit(pattern(inputPattern), artifact)

        then:
        1 * repo.list(repoPath) >> ['1.2']

        where:
        inputPattern                                  | repoPath
        "/[organisation]/[revision]"                  | "/org.acme/"
        "/[organization]/[revision]"                  | "/org.acme/"
        "/[module]/[revision]"                        | "/proj1/"
        "/[module]/[revision]-lib.[ext]"              | "/proj1/"
        "/[organisation]/[module]/[revision]"         | "/org.acme/proj1/"
        "/[revision]/[module]/[organisation]"         | "/"
        "/[type]s/[module]/[organisation]/[revision]" | "/jars/proj1/org.acme/"
    }

    def "visit returns empty version list when pattern has no revision token"() {
        setup:
        repo.list(_) >> repoResult

        when:
        def versionList = lister.getVersionList(moduleRevisionId)
        versionList.visit(pattern(testPattern), artifact)

        then:
        versionList.empty

        where:
        testPattern                      | repoResult
        "/some/pattern/with/no/revision" | ["/some/1-version", "/some/2.1-version", "/some/a-version-version"]
    }

    def pattern(String pattern) {
        ResourcePattern resourcePattern = Mock()
        _ * resourcePattern.pattern >> pattern
        return resourcePattern
    }
}
