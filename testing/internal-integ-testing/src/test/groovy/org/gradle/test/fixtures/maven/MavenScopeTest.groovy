/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.test.fixtures.maven

import spock.lang.Specification

class MavenScopeTest extends Specification {

    def "finds expected dependency"() {
        given:
        MavenDependency givenMavenDependency = new MavenDependency(groupId: 'org.gradle', artifactId: 'test', version: '1.0')
        MavenScope mavenScope = new MavenScope(dependencies: ['org.gradle:test:1.0': givenMavenDependency])

        when:
        MavenDependency expectedMavenDependency = mavenScope.expectDependency('org.gradle:test:1.0')

        then:
        givenMavenDependency == expectedMavenDependency
    }

    def "throws exception if dependency cannot be found"() {
        given:
        MavenDependency givenMavenDependency = new MavenDependency(groupId: 'org.gradle', artifactId: 'test', version: '1.0')
        MavenScope mavenScope = new MavenScope(dependencies: ['org.gradle:test:1.0': givenMavenDependency])

        when:
        mavenScope.expectDependency('org.gradle:other:1.0')

        then:
        Throwable t = thrown(AssertionError)
        t.message == "Could not find expected dependency org.gradle:other:1.0. Actual: ${[givenMavenDependency]}"
    }

    def "checks if dependency declares exclusion"() {
        given:
        MavenDependency givenMavenDependency = new MavenDependency(groupId: 'org.gradle', artifactId: 'test', version: '1.0')
        givenMavenDependency.exclusions = givenExclusions
        MavenScope mavenScope = new MavenScope(dependencies: ['org.gradle:test:1.0': givenMavenDependency])

        when:
        boolean foundExclusion = mavenScope.hasDependencyExclusion('org.gradle:test:1.0', expectedExclusion)

        then:
        foundExclusion == result

        where:
        givenExclusions                               | expectedExclusion                           | result
        [createExclusion('com.company', 'important')] | null                                        | false
        [createExclusion('com.company', 'important')] | createExclusion('com.company', 'other')     | false
        [createExclusion('com.company', 'important')] | createExclusion('com.company', 'important') | true
    }

    static MavenDependencyExclusion createExclusion(String groupId, String artifactId) {
        new MavenDependencyExclusion(groupId, artifactId)
    }
}
