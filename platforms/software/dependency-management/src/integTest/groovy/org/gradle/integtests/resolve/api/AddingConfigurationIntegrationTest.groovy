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

package org.gradle.integtests.resolve.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class AddingConfigurationIntegrationTest extends AbstractIntegrationSpec {
    def "can add configurations" () {
        buildFile << """
            def file1 = file('file1')
            def file2 = file('file2')

            configurations {
                conf1
                conf2
            }

            dependencies {
                conf1 files(file1)
                conf2 files(file2)
            }

            task addConfigs {
                def files1 = configurations.conf1
                def files2 = configurations.conf2
                doLast {
                    FileCollection sum = files1
                    sum += files2
                    assert sum.files.sort() == [ file1, file2 ]
                }
            }
        """

        expect:
        succeeds "addConfigs"
    }

    def "can subtract configurations" () {
        buildFile << """
            def file1 = file('file1')
            def file2 = file('file2')
            def file3 = file('file3')

            configurations {
                conf1
                conf2
                conf3
            }

            dependencies {
                conf1 files(file1)
                conf2 files(file2)
                conf3 files(file1, file2, file3)
            }

            task addConfigs {
                def files1 = configurations.conf1
                def files2 = configurations.conf2
                def files3 = configurations.conf3
                doLast {
                    FileCollection difference = files3
                    difference -= files2
                    difference -= files1
                    assert difference.files.sort() == [ file3 ]
                }
            }
        """

        expect:
        succeeds "addConfigs"
    }

    def "can remove and add configurations between resolutions"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()

        buildFile << """
            ${mavenTestRepository()}

            def conf = configurations.create("conf")
            conf.dependencies.add(project.dependencies.create("org:foo:1.0"))
            conf.files
            configurations.remove(conf)

            def conf2 = configurations.create("conf2")
            conf2.dependencies.add(project.dependencies.create("org:foo:1.0"))
            conf2.files
        """

        expect:
        succeeds("help")
    }

    def "can remove and add configurations with no dependencies between resolutions"() {
        given:
        buildFile << """
            def conf = configurations.create("conf")
            assert conf.files.empty
            configurations.remove(conf)

            def conf2 = configurations.create("conf2")
            assert conf2.files.empty
        """

        expect:
        succeeds("help")
    }

    def "can remove configuration without dependencies and resolve it"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()

        buildFile << """
            ${mavenTestRepository()}

            def conf = configurations.create("conf")
            configurations.remove(conf)

            tasks.register("resolve") {
                def files = conf.incoming.files
                doLast {
                    assert files.empty
                }
            }

        """

        expect:
        succeeds("resolve")
    }

    def "can remove configuration with dependencies and resolve it"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()

        buildFile << """
            ${mavenTestRepository()}

            def conf = configurations.create("conf")
            conf.dependencies.add(project.dependencies.create("org:foo:1.0"))
            configurations.remove(conf)

            tasks.register("resolve") {
                def files = conf.incoming.files
                doLast {
                    assert files*.name == ["foo-1.0.jar"]
                }
            }
        """

        when:

        expect:
        succeeds("resolve")
    }
}
