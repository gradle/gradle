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

package org.gradle.integtests.resolve.maven

import org.gradle.integtests.resolve.AbstractDependencyResolutionTest

class LegacyMavenRepoResolveIntegrationTest extends AbstractDependencyResolutionTest {
    def "can configure legacy Maven resolver to verify artifact using checksums"() {
        server.start()

        given:
        def module = mavenHttpRepo.module("group", "module", "1.2").publishWithChangedContent()
        buildFile << """
repositories {
    def repo = mavenRepo url: '${mavenHttpRepo.uri}'
    repo.checksums = 'sha1,md5'
}

configurations {
    check
}

dependencies {
    check 'group:module:1.2'
}

task check << {
    configurations.check.files*.name == 'module-1.2.jar'
}
"""
        and:
        module.expectPomGet()
        module.expectPomSha1GetMissing()
        module.expectPomMd5Get()
        module.artifact.expectGet()
        module.artifact.sha1.expectGetMissing()
        module.artifact.expectMd5Get()

        expect:
        succeeds 'check'

        when:
        module.publishWithChangedContent()

        and:
        server.resetExpectations()
        module.expectPomHead()
        module.expectPomSha1Get()
        module.expectPomGet()
        // TODO - shouldn't get checksum twice
        module.expectPomSha1Get()
        module.artifact.expectHead()
        module.artifact.sha1.expectGet()
        module.artifact.expectGet()
        // TODO - shouldn't get checksum twice
        module.artifact.sha1.expectGet()

        then:
        executer.withArguments("--refresh-dependencies")
        succeeds 'check'
    }

    def "fails when checksum does not match artifact contents"() {
        server.start()

        given:
        def module = mavenHttpRepo.module("group", "module", "1.2").publishWithChangedContent()
        module.artifactSha1File.text = '1234'

        buildFile << """
repositories {
    def repo = mavenRepo url: '${mavenHttpRepo.uri}'
    repo.checksums = 'sha1'
}

configurations {
    check
}

dependencies {
    check 'group:module:1.2'
}

task check << {
    configurations.check.files*.name == 'module-1.2.jar'
}
"""
        and:
        module.expectPomGet()
        module.expectPomSha1Get()
        module.artifact.expectGet()
        module.artifact.sha1.expectGet()

        expect:
        fails 'check'
        failureHasCause("Could not download artifact 'group:module:1.2@jar'")
        failureHasCause("invalid sha1: expected=1234 computed=5b253435f362abf1a12197966e332df7d2b153f5")
    }

    def "can configure resolver to fail when descriptor is not present"() {
        server.start()

        given:
        def module = mavenHttpRepo.module("group", "module", "1.2").publish()

        buildFile << """
repositories {
    def repo = mavenRepo url: '${mavenHttpRepo.uri}'
    repo.descriptor = "required"
}

configurations {
    check
}

dependencies {
    check 'group:module:1.2'
}

task check << {
    configurations.check.files
}
"""
        and:
        module.expectPomGetMissing()

        expect:
        fails 'check'
        failureHasCause("Could not find group:group, module:module, version:1.2.")
    }

    def "can configure resolver to ignore poms"() {
        server.start()

        given:
        def module = mavenHttpRepo.module("group", "module", "1.2").publish()

        buildFile << """
repositories {
    def repo = mavenRepo url: '${mavenHttpRepo.uri}'
    repo.usepoms = false
}

configurations {
    check
}

dependencies {
    check 'group:module:1.2'
}

task check << {
    configurations.check.files*.name == 'module-1.2.jar'
}
"""
        and:
        // TODO - do not need this head request
        module.artifact.expectHead()
        module.artifact.expectGet()

        expect:
        succeeds "check"
    }

    def "can configure resolver to ignore maven-metadata.xml when resolving snapshots"() {
        server.start()

        given:
        def module = mavenHttpRepo.module("group", "module", "1.2-SNAPSHOT").withNonUniqueSnapshots().publish()

        buildFile << """
repositories {
    def repo = mavenRepo url: '${mavenHttpRepo.uri}'
    repo.useMavenMetadata = false
}

configurations {
    check
}

dependencies {
    check 'group:module:1.2-SNAPSHOT'
}

task check << {
    configurations.check.files*.name == 'module-1.2-SNAPSHOT.jar'
}
"""
        and:
        module.expectPomGet()
        module.artifact.expectGet()

        expect:
        executer.withDeprecationChecksDisabled()
        succeeds "check"
    }
}
