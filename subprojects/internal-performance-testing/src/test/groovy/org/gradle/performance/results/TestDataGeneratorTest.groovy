/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.performance.results

import groovy.json.JsonSlurper
import org.gradle.performance.ResultSpecification
import spock.lang.Subject

class TestDataGeneratorTest extends ResultSpecification {
    @Subject
    TestDataGenerator generator = new TestDataGenerator()
    StringWriter writer = new StringWriter()

    def "can generate cross version json data"() {
        when:
        generator.render(mockCrossVersionHistory(), writer)

        then:
        assert new JsonSlurper().parseText(writer.toString()) == [
            executionLabels: [
                [
                    id: '1450575490',
                    branch: 'master',
                    date: '1970-01-01',
                    commits: ['123456']
                ],
                [
                    id: '1450575490',
                    branch: 'master',
                    date: '1970-01-01',
                    commits: ['123456']
                ]
            ],
            totalTime: [
                [
                    label: '5.0-mockbaseline-1',
                    data: [[0, 1]]
                ],
                [
                    label: '5.0-mockbaseline-2',
                    data: [[1, 2]]
                ],
                [
                    label: 'master',
                    data: [[0, 2], [1, 1]]
                ]
            ],
            difference: [
                [
                    label: 'master vs 5.0-mockbaseline-1',
                    data: [[0, 100]]
                ],
                [
                    label: 'master vs 5.0-mockbaseline-2',
                    data: [[1, -50]]
                ],
            ],
            confidence: [
                [
                    label: 'master vs 5.0-mockbaseline-1',
                    data: [[0, 68.27]]
                ],
                [
                    label: 'master vs 5.0-mockbaseline-2',
                    data: [[1, 68.27]]
                ],
            ],
            background: [
                [xaxis: [from: -0.5, to: 0.5], color: "#ff0000"],
                [xaxis: [from: 0.5, to: 1.5], color: "#00ff00"]
            ]
        ]
    }

    def "can generate cross build json data"() {
        when:
        generator.render(mockCrossBuildHistory(), writer)

        then:
        assert new JsonSlurper().parseText(writer.toString()) == [
            executionLabels: [
                [
                    id: '1424385918',
                    branch: 'master',
                    date: '1970-01-01',
                    commits: ['abcdef']
                ],
                [
                    id: '1424385918',
                    branch: 'master',
                    date: '1970-01-01',
                    commits: ['abcdef']
                ]
            ],
            totalTime: [
                [
                    label: 'build1',
                    data: [[0, 1]]
                ],
                [
                    label: 'build2',
                    data: [[0, 2]]
                ]
            ]
        ]
    }
}
