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
import org.gradle.api.resources.ResourceException
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.resolve.result.DefaultBuildableModuleVersionListingResolveResult
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ExternalResourceRepository
import spock.lang.Specification

class ResourceVersionListerTest extends Specification {

    def repo = Mock(ExternalResourceRepository)
    def module = new DefaultModuleIdentifier("org.acme", "proj1")
    def moduleVersion = new DefaultModuleVersionIdentifier(module, "1.0")
    def artifact = new DefaultIvyArtifactName("proj1", "jar", "jar")
    def result = new DefaultBuildableModuleVersionListingResolveResult()

    ResourceVersionLister lister;

    def setup() {
        lister = new ResourceVersionLister(repo)
    }

    def "listVersions propagates Exceptions as ResourceException"() {
        setup:
        def failure = new RuntimeException("Test IO Exception")
        def resource = Stub(ExternalResource)
        def testPattern = pattern("/a/pattern/with/[revision]/")
        _ * repo.resource(_) >> resource
        _ * resource.list() >> { throw failure }

        when:
        lister.listVersions(module, artifact, [testPattern], result)

        then:
        ResourceException e = thrown()
        e.message == "Could not list versions using Ivy pattern '/a/pattern/with/[revision]/'."
        e.cause == failure
    }

    def "produces no result for missing resource"() {
        setup:
        def resource = Stub(ExternalResource)
        _ * repo.resource(_) >> resource
        _ * resource.list() >> null

        when:
        lister.listVersions(module, artifact, [pattern(testPattern)], result)

        then:
        !result.hasResult()

        where:
        testPattern << ["/some/[revision]", "/some/version-[revision]"]
    }

    def "produces no result when repository contains empty list"() {
        setup:
        def resource = Stub(ExternalResource)
        _ * repo.resource(_) >> resource
        _ * resource.list() >> []

        when:
        lister.listVersions(module, artifact, [pattern("/some/[revision]")], result)

        then:
        !result.hasResult()
    }

    def "resolves versions from pattern with '#testPattern'"() {
        def resource = Mock(ExternalResource)

        when:
        lister.listVersions(module, artifact, [pattern(testPattern)], result)
        def versions = result.versions

        then:
        versions == ["1", "2.1", "a-version"] as Set

        and:
        1 * repo.resource(new ExternalResourceName(repoListingPath)) >> resource
        1 * resource.list() >> repoResult
        0 * _

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
        def resource1 = Mock(ExternalResource)
        def resource2 = Mock(ExternalResource)

        when:
        def pattern1 = pattern("/[revision]/[artifact]-[revision].[ext]")
        def pattern2 = pattern("/[organisation]/[revision]/[artifact]-[revision].[ext]")
        lister.listVersions(module, artifact, [pattern1, pattern2], result)
        def versions = result.versions

        then:
        versions == ["1.2", "1.3", "1.3", "1.4"] as Set

        and:
        1 * repo.resource(new ExternalResourceName("/")) >> resource1
        1 * resource1.list() >> ["1.2", "1.3"]
        1 * repo.resource(new ExternalResourceName("/org.acme/")) >> resource2
        1 * resource2.list() >> ["1.3", "1.4"]
        0 * _
    }

    def 'overlapping patterns filter out parts matching more than one pattern'() {
        def resource1 = Mock(ExternalResource)

        when:
        def pattern1 = pattern("/[organisation]/[module]/[revision]/ivy-[revision].xml")
        def pattern2 = pattern("/[organisation]/[module]/ivy-[revision].xml")
        lister.listVersions(module, artifact, [pattern1, pattern2], result)
        def versions = result.versions

        then:
        versions == ['1.0.0', '1.5.0', '2.0.0'] as Set

        and:
        1 * repo.resource(new ExternalResourceName('/org.acme/proj1/')) >> resource1
        1 * resource1.list() >> ['1.0.0', '1.5.0', 'ivy-2.0.0.xml']
        0 * _
    }

    def 'exact duplicates do not filter out all results'() {
        def resource1 = Mock(ExternalResource)

        when:
        def pattern1 = pattern("/[organisation]/[module]/ivy-[revision].xml")
        def pattern2 = pattern("/[organisation]/[module]/ivy-[revision].xml")
        lister.listVersions(module, artifact, [pattern1, pattern2], result)
        def versions = result.versions

        then:
        versions == ['1.0.0', '2.0.0'] as Set

        and:
        1 * repo.resource(new ExternalResourceName('/org.acme/proj1/')) >> resource1
        1 * resource1.list() >> ['ivy-1.0.0.xml', 'ivy-2.0.0.xml']
        0 * _
    }

    def "ignores duplicate patterns"() {
        def resource = Mock(ExternalResource)

        when:
        def patternA = pattern("/a/[revision]/[artifact]-[revision].[ext]")
        def patternB = pattern("/a/[revision]/[artifact]-[revision]")
        lister.listVersions(module, artifact, [patternA, patternB], result)
        def versions = result.versions

        then:
        versions == ["1.2", "1.3"] as Set

        and:
        1 * repo.resource(new ExternalResourceName("/a/")) >> resource
        1 * resource.list() >> ["1.2", "1.3"]
        0 * _
    }

    def "substitutes non revision placeholders from pattern before hitting repository"() {
        def resource = Mock(ExternalResource)

        when:
        lister.listVersions(module, artifact, [pattern(inputPattern)], result)

        then:
        1 * repo.resource(new ExternalResourceName(repoPath)) >> resource
        1 * resource.list() >> ['1.2']
        0 * _

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

    def "produces no result when pattern has no revision token"() {
        setup:
        repo.list(_) >> repoResult

        when:
        lister.listVersions(module, artifact, [pattern(testPattern)], result)

        then:
        !result.hasResult()

        where:
        testPattern                      | repoResult
        "/some/pattern/with/no/revision" | ["/some/1-version", "/some/2.1-version", "/some/a-version-version"]
    }

    def pattern(String pattern) {
        return new IvyResourcePattern(pattern)
    }
}
