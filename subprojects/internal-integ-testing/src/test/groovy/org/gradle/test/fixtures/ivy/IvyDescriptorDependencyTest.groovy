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

package org.gradle.test.fixtures.ivy

import spock.lang.Specification

class IvyDescriptorDependencyTest extends Specification {

    def "can check if given confs attribute is set"() {
        given:
        IvyDescriptorDependency ivyDescriptorDependency = new IvyDescriptorDependency(confs: [givenConf])

        when:
        boolean matchingConf = ivyDescriptorDependency.hasConf(expectedConf)

        then:
        matchingConf == result

        where:
        givenConf | expectedConf | result
        null      | 'compile'    | false
        'compile' | null         | false
        null      | null         | true
        'compile' | 'compile'    | true
        'compile' | 'runtime'    | false
    }

    def "can check if transitive enabled"() {
        given:
        IvyDescriptorDependency ivyDescriptorDependency = new IvyDescriptorDependency(transitive: givenTransitive)

        when:
        boolean transitiveEnabled = ivyDescriptorDependency.transitiveEnabled()

        then:
        transitiveEnabled == result

        where:
        givenTransitive | result
        'true'          | true
        'false'         | false
    }

    def "can check if excludes are defined"() {
        given:
        IvyDescriptorDependency ivyDescriptorDependency = new IvyDescriptorDependency(exclusions: givenExclusions)

        when:
        boolean excludes = ivyDescriptorDependency.hasExcludes()

        then:
        excludes == result

        where:
        givenExclusions                          | result
        []                                       | false
        [new IvyDescriptorDependencyExclusion()] | true
    }

    def "can check if specific exclude is defined"() {
        given:
        IvyDescriptorDependency ivyDescriptorDependency = new IvyDescriptorDependency(exclusions: givenExclusions)

        when:
        boolean excludes = ivyDescriptorDependency.hasExclude(expectedExclusion)

        then:
        excludes == result

        where:
        givenExclusions                                    | expectedExclusion                               | result
        []                                                 | null                                            | false
        []                                                 | createExclusion('org.gradle', 'gradle', 'test') | false
        [createExclusion('org.gradle', 'gradle', 'other')] | createExclusion('org.gradle', 'gradle', 'test') | false
        [createExclusion('org.gradle', 'gradle', 'test')]  | createExclusion('org.gradle', 'gradle', 'test') | true
    }

    static IvyDescriptorDependencyExclusion createExclusion(String org, String module, String name, String type = 'jar', String ext = 'jar',
                                                            String conf = 'compile', String matcher = 'regexp') {
        new IvyDescriptorDependencyExclusion(org: org, module: module, name: name, type: type, ext: ext, conf: conf, matcher: matcher)
    }
}
