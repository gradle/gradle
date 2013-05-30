/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ApplicationIntegrationSpec extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            apply plugin: 'application'
            applicationName = 'app'
            mainClassName = "something"
        """
    }

    def "conventional resources are including in dist"() {
        when:
        file("src/dist/dir").with {
            file("r1.txt") << "r1"
            file("r2.txt") << "r2"
        }

        then:
        succeeds "installApp"

        and:
        def distBase = file("build/install/app")
        distBase.file("dir").directory
        distBase.file("dir/r1.txt").text == "r1"
        distBase.file("dir/r2.txt").text == "r2"
    }

    def "configure the distribution spec to source from a different dir"() {
        when:
        file("src/somewhere-else/dir").with {
            file("r1.txt") << "r1"
            file("r2.txt") << "r2"
        }

        and:
        buildFile << """
            applicationDistribution.from("src/somewhere-else") {
                include "**/r2.*"
            }
        """

        then:
        succeeds "installApp"

        and:
        def distBase = file("build/install/app")
        distBase.file("dir").directory
        !distBase.file("dir/r1.txt").exists()
        distBase.file("dir/r2.txt").text == "r2"
    }

    def "distribution file producing tasks are run automatically"() {
        when:
        buildFile << """
            task createDocs {
                def docs = file("\$buildDir/docs")

                outputs.dir docs

                doLast {
                    assert docs.mkdirs()
                    new File(docs, "readme.txt") << "Read me!!!"
                }
            }

            applicationDistribution.from(createDocs) {
                into "docs"
                rename 'readme(.*)', 'READ-ME\$1'
            }
        """

        then:
        succeeds "installApp"

        and:
        ":createDocs" in nonSkippedTasks

        and:
        def distBase = file("build/install/app")
        distBase.file("docs").directory
        !distBase.file("docs/readme.txt").exists()
        distBase.file("docs/READ-ME.txt").text == "Read me!!!"
    }

}