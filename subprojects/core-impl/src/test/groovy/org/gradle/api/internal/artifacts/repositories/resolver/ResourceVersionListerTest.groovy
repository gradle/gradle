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
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ExactVersionMatcher
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionStrategy
import org.gradle.api.internal.artifacts.metadata.DefaultIvyArtifactName
import org.gradle.internal.resource.transport.ExternalResourceRepository
import org.gradle.internal.resource.ResourceException
import spock.lang.Specification
import spock.lang.Unroll

class ResourceVersionListerTest extends Specification {

    def repo = Mock(ExternalResourceRepository)
    def moduleRevisionId = IvyUtil.createModuleRevisionId("org.acme", "proj1", "1.0")
    def module = new DefaultModuleIdentifier("org.acme", "proj1")
    def moduleVersion = new DefaultModuleVersionIdentifier(module, "1.0")
    def artifact = new DefaultIvyArtifactName("proj1", "jar", "jar")

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
        def versionList = lister.getVersionList(module)
        versionList.visit(testPattern, artifact)

        then:
        ResourceException e = thrown()
        e.message == "Could not list versions using Ivy pattern '/a/pattern/with/[revision]/'."
        e.cause == failure
    }

    def "visit produces empty versionList for missing resource"() {
        setup:
        1 * repo.list(_) >> null

        when:
        def versionList = lister.getVersionList(module)
        versionList.visit(pattern(testPattern), artifact)

        then:
        versionList.empty

        where:
        testPattern << ["/some/[revision]", "/some/version-[revision]"]
    }

    def "visit returns empty VersionList when repository contains empty list"() {
        setup:
        1 * repo.list(_) >> []

        when:
        def versionList = lister.getVersionList(module)
        versionList.visit(pattern("/some/[revision]"), artifact)

        then:
        versionList.empty
    }

    @Unroll
    def "visit resolves versions from pattern with '#testPattern'"() {
        when:
        def versionList = lister.getVersionList(module)
        versionList.visit(pattern(testPattern), artifact)

        then:
        versionList.versions == ["1", "2.1", "a-version"] as Set

        and:
        1 * repo.list(URI.create(repoListingPath)) >> repoResult
        0 * repo._

        where:
        testPattern                              | repoListingPath | repoResult
        "[revision]"                             | ""              | ["1", "2.1", "a-version"]
        "[revision]/"                            | ""              | ["1", "2.1", "a-version"]
        "/[revision]"                            | "/"             | ["1", "2.1", "a-version"]
        "/[revision]/"                           | "/"             | ["1", "2.1", "a-version"]
        "/some/[revision]"                       | "/some/"        | ["1", "2.1", "a-version"]
        "/some/[revision]/"                      | "/some/"        | ["1", "2.1", "a-version"]
        "/some/[revision]/lib"                   | "/some/"        | ["1", "2.1", "a-version"]
        "/some/version-[revision]"               | "/some/"        | ["version-1", "version-2.1", "version-a-version", "nonmatching"]
        "/some/version-[revision]/lib"           | "/some/"        | ["version-1", "version-2.1", "version-a-version", "nonmatching"]
        "/some/version-[revision]/lib/"          | "/some/"        | ["version-1", "version-2.1", "version-a-version", "nonmatching"]
        "/some/[revision]-version"               | "/some/"        | ["1-version", "2.1-version", "a-version-version", "nonmatching"]
        "/some/[revision]-version/lib"           | "/some/"        | ["1-version", "2.1-version", "a-version-version", "nonmatching"]
        "/some/[revision]-lib.[ext]"             | "/some/"        | ["1-lib.jar", "1-lib.zip", "2.1-lib.jar", "a-version-lib.jar", "nonmatching"]
        "/some/any-[revision]-version/lib"       | "/some/"        | ["any-1-version", "any-2.1-version", "any-a-version-version", "nonmatching"]
        "/some/any-[revision]-version/lib/"      | "/some/"        | ["any-1-version", "any-2.1-version", "any-a-version-version", "nonmatching"]
        "/some/[revision]/lib/myjar-[revision]/" | "/some/"        | ["1", "2.1", "a-version"]
        "/some/proj-[revision]/[revision]/lib/"  | "/some/"        | ["proj-1", "proj-2.1", "proj-a-version"]
    }

    def "visit builds union of versions"() {
        when:
        def versionList = lister.getVersionList(module)
        def pattern1 = pattern("/[revision]/[artifact]-[revision].[ext]")
        def pattern2 = pattern("/[organisation]/[revision]/[artifact]-[revision].[ext]")
        versionList.visit(pattern1, artifact)
        versionList.visit(pattern2, artifact)

        then:
        versionList.versions == ["1.2", "1.3", "1.4"] as Set

        and:
        1 * repo.list(URI.create("/")) >> ["1.2", "1.3"]
        1 * repo.list(URI.create("/org.acme/")) >> ["1.3", "1.4"]
        0 * repo._
    }

    def "visit ignores duplicate patterns"() {
        when:
        def versionList = lister.getVersionList(module)
        final patternA = pattern("/a/[revision]/[artifact]-[revision].[ext]")
        versionList.visit(patternA, artifact)
        versionList.visit(pattern("/a/[revision]/[artifact]-[revision]"), artifact)

        then:
        versionList.versions == ["1.2", "1.3"] as Set

        and:
        1 * repo.list(URI.create("/a/")) >> ["1.2", "1.3"]
        0 * repo._
    }

    def "visit substitutes non revision placeholders from pattern before hitting repository"() {
        when:
        def versionList = lister.getVersionList(module)
        versionList.visit(pattern(inputPattern), artifact)

        then:
        1 * repo.list(URI.create(repoPath)) >> ['1.2']

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
        def versionList = lister.getVersionList(module)
        versionList.visit(pattern(testPattern), artifact)

        then:
        versionList.empty

        where:
        testPattern                      | repoResult
        "/some/pattern/with/no/revision" | ["/some/1-version", "/some/2.1-version", "/some/a-version-version"]
    }

    def pattern(String pattern) {
        return new IvyResourcePattern(pattern)
    }
}
