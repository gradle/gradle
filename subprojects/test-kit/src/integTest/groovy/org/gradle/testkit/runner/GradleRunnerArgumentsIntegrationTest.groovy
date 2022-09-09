/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner

class GradleRunnerArgumentsIntegrationTest extends BaseGradleRunnerIntegrationTest {

    def "can execute build without specifying any arguments"() {
        given:
        buildScript """
            help {
                doLast {
                    file('out.txt').text = "help"
                }
            }
        """

        when:
        runner().build()

        then:
        file("out.txt").text == "help"
    }

    def "can execute build with multiple tasks"() {
        given:
        buildScript """
            task t1 {
                doLast {
                    file("out.txt").text = "t1"
                }
            }
            task t2 {
                doLast {
                    file("out.txt") << "t2"
                }
            }
        """

        when:
        runner('t1', 't2').build()

        then:
        file("out.txt").text == "t1t2"
    }

    def "can provide non task arguments"() {
        given:
        buildScript """
            task writeValue {
                doLast {
                    file("out.txt").text = project.value
                }
            }
        """

        when:
        runner("writeValue", "-Pvalue=foo").build()

        then:
        file("out.txt").text == "foo"
    }

    def "can enable parallel execution via --parallel property"() {
        given:
        buildScript """
            task writeValue {
                doLast {
                    file("out.txt").text = gradle.startParameter.parallelProjectExecutionEnabled
                }
            }
        """

        when:
        runner("writeValue", "--parallel")
            .withGradleVersion(lowestMajorGradleVersion)
            .build()

        then:
        file("out.txt").text == "true"
    }
}
