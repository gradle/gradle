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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest

class LegacyMavenRepoResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def "can configure resolver to fail when descriptor is not present"() {
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
        module.pom.expectGetMissing()

        and:
        executer.withDeprecationChecksDisabled()

        expect:
        fails 'check'
        failureHasCause("Could not find group:module:1.2.")
    }

    def "can configure resolver to ignore poms"() {
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

        and:
        executer.withDeprecationChecksDisabled()

        expect:
        succeeds "check"
    }

    def "can configure resolver to ignore maven-metadata.xml when resolving snapshots"() {
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
        module.pom.expectGet()
        module.artifact.expectGet()

        and:
        executer.withDeprecationChecksDisabled()

        expect:
        succeeds "check"
    }
}
