/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.resolve.caching

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf
import spock.lang.Issue

@IgnoreIf({ GradleContextualExecuter.parallel })
// no point, always runs in parallel
class ConcurrentDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        requireOwnGradleUserHomeDir()
        executer.withArgument('--parallel')
        executer.withArgument('--max-workers=8')
    }

    @Issue("gradle/performance#502")
    def "local component selection harness test: thread-safety"() {
        given:
        buildFile << '''
             def usage = Attribute.of('usage', String)
             allprojects {

                dependencies {
                    attributesSchema {
                        attribute(usage)
                    }
                }
                configurations {
                    compile {
                        attributes { attribute usage, 'api' }
                    }
                    test {
                        attributes { attribute usage, 'test' }
                    }
                    other {
                        attributes { attribute usage, 'other' }
                    }
                    'default'
                }

                task resolve {
                    def files = configurations.compile
                    doLast {
                        files.files
                    }
                }
            }

        '''
        int groups = 20
        int iterations = 100
        groups.times { group ->
            def pfile = file("project$group/build.gradle")
            pfile << """

                dependencies {
            """
            iterations.times { i ->
                file("project_${i}/build.gradle") << ''
                pfile << """
                    compile project(":project_${i}")
"""
            }
            pfile << '''
                }
'''
        }

        settingsFile << """
            ${groups}.times { group ->
                include "project\${group}"
            }
            ${iterations}.times { i ->
                include "project_\${i}"
            }
        """

        when:
        run 'resolve'

        then:
        noExceptionThrown()
    }
}
