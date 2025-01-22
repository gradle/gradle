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

package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.archives.TestReproducibleArchives
import org.gradle.test.fixtures.dsl.GradleDsl

/**
 * Tests {@link org.gradle.api.distribution.plugins.DistributionBasePlugin}
 */
@TestReproducibleArchives
class DistributionBasePluginIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << """
            rootProject.name = 'TestProject'
        """

        file("someFile").createFile()
    }

    def "can apply base distribution plugin to empty project"() {
        buildFile << """
            plugins {
                id("distribution-base")
            }

            assert distributions.isEmpty()
        """

        expect:
        succeeds("help")
    }

    def "can create a custom distribution in #dsl"() {
        def file = dsl == GradleDsl.GROOVY ? buildFile : buildKotlinFile
        file << """
            plugins {
                id("distribution-base")
            }

            distributions {
                create("custom") {
                    distributionBaseName = "customName"
                    contents {
                        from("src/customLocation")
                    }
                }
            }

            assert(distributions.findByName("main") == null)
        """

        expect:
        succeeds("help")

        where:
        dsl << [GradleDsl.GROOVY, GradleDsl.KOTLIN]
    }

    def createTaskForCustomDistribution() {
        buildFile << """
            plugins {
                id("distribution-base")
            }

            distributions {
                custom {
                    contents {
                        from { "someFile" }
                    }
                }
            }
        """

        when:
        succeeds('customDistZip')

        then:
        file('build/distributions/TestProject-custom.zip').usingNativeTools().unzipTo(file("unzip"))
        file("unzip/TestProject-custom/someFile").assertIsFile()
    }

    def createTaskForCustomDistributionWithCustomName() {
        given:
        buildFile << """
            plugins {
                id("distribution-base")
            }

            distributions {
                custom {
                    distributionBaseName = 'customName'
                    contents {
                        from { "someFile" }
                    }
                }
            }
        """

        when:
        succeeds('customDistZip')

        then:
        file('build/distributions/customName.zip').usingNativeTools().unzipTo(file("unzip"))
        file("unzip/customName/someFile").assertIsFile()
    }

    def createTaskForCustomDistributionWithEmptyCustomName() {
        given:
        buildFile << """
            plugins {
                id("distribution-base")
            }

            distributions {
                custom{
                    distributionBaseName = ''
                    contents {
                        from { "someFile" }
                    }
                }
            }
        """

        when:
        runAndFail('customDistZip')

        then:
        failure.assertHasCause "Distribution 'custom' must not have an empty distributionBaseName."
    }

    def createCreateArchiveForCustomDistribution(){
        given:
        createDir('src/custom/dist') {
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
            }
        }

        buildFile << """
            plugins {
                id("distribution-base")
            }

            distributions{
                custom
            }
        """

        when:
        run('customDistZip')

        then:
        file('build/distributions/TestProject-custom.zip').exists()
    }

    def includeFileFromSrcMainCustom() {
        given:
        createDir('src/custom/dist'){
            file 'file1.txt'
            dir {
                file 'file2.txt'
            }
        }

        buildFile << """
            plugins {
                id("distribution-base")
            }

            version = 1.2

            distributions{
                custom
            }
        """

        when:
        run('customDistZip')

        then:
        file('build/distributions/TestProject-custom-1.2.zip').usingNativeTools().unzipTo(file("unzip"))
        file("unzip").assertHasDescendants('TestProject-custom-1.2/file1.txt', 'TestProject-custom-1.2/dir/file2.txt')
    }

    def includeFileFromDistContent() {
        given:
        createDir('src/custom/dist'){
            file 'file1.txt'
            dir {
                file 'file2.txt'
            }
        }

        createDir("docs"){
            file 'file3.txt'
            dir2 {
                file 'file4.txt'
            }
        }

        buildFile << """
            plugins {
                id("distribution-base")
            }

            version = 1.2

            distributions{
                custom {
                    contents {
                        from ( 'docs' ){
                            into 'docs'
                        }
                    }
                }
            }
        """

        when:
        run('customDistZip')

        then:
        file('build/distributions/TestProject-custom-1.2.zip').usingNativeTools().unzipTo(file("unzip"))
        file("unzip").assertHasDescendants(
            'TestProject-custom-1.2/file1.txt',
            'TestProject-custom-1.2/dir/file2.txt',
            'TestProject-custom-1.2/docs/file3.txt',
            'TestProject-custom-1.2/docs/dir2/file4.txt')
    }

    def installFromDistContent() {
        given:
        createDir('src/custom/dist'){
            file 'file1.txt'
            dir {
                file 'file2.txt'
            }
        }
        createDir("docs"){
            file 'file3.txt'
            dir2 {
                file 'file4.txt'
            }
        }
        and:
        buildFile << """
            plugins {
                id("distribution-base")
            }

            version = 1.2

            distributions{
                custom {
                    contents {
                        from ( 'docs' ){
                            into 'docs'
                        }
                    }
                }
            }
        """

        when:
        run('installCustomDist')

        then:
        file('build/install/TestProject-custom').exists()
        file('build/install/TestProject-custom').assertHasDescendants(
            'file1.txt',
            'dir/file2.txt',
            'docs/file3.txt',
            'docs/dir2/file4.txt')
    }

    def installDistCanBeRerun() {
        buildFile << """
            plugins {
                id("distribution-base")
            }

            distributions {
                custom {
                    contents {
                        from { "someFile" }
                    }
                }
            }

        """

        expect:
        succeeds('installCustomDist')

        when:
        // update the file so that when it re-runs it is not UP-TO-DATE
        file("someFile") << "updated"
        succeeds('installCustomDist')

        then:
        file('build/install/TestProject-custom/someFile').assertIsCopyOf(file("someFile"))
    }

    def createTarTaskForCustomDistribution() {
        buildFile << """
            apply plugin:'distribution'

            distributions {
                custom {
                    contents {
                        from { "someFile" }
                    }
                }
            }

        """

        when:
        succeeds('customDistTar')

        then:
        file('build/distributions/TestProject-custom.tar').usingNativeTools().untarTo(file("untar"))
        file("untar/TestProject-custom/someFile").assertIsFile()
    }

}
