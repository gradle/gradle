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

import org.gradle.integtests.fixtures.WellBehavedPluginTest

import static org.hamcrest.Matchers.containsString

class DistributionPluginIntegrationTest extends WellBehavedPluginTest {

    @Override
    String getPluginId() {
        "distribution"
    }

    String getMainTask() {
        return "distZip"
    }

    def setup() {
        file("settings.gradle").text = "rootProject.name='TestProject'"
        file("someFile").createFile()
    }

    def createTaskForCustomDistribution() {
        when:
        buildFile << """
            apply plugin:'distribution'

            distributions {
                custom{
                    contents {
                        from { "someFile" }
                    }
                }
            }

            """
        then:
        succeeds('customDistZip')
        and:
        file('TestProject-custom.zip').usingNativeTools().unzipTo(file("unzip"))
        file("unzip/someFile").assertIsFile()
    }

    def createTaskForCustomDistributionWithCustomName() {
        when:
        buildFile << """
            apply plugin:'distribution'

            distributions {
                custom{
                    baseName='customName'
                    contents {
                        from { "someFile" }
                    }
                }
            }
            """
        then:
        succeeds('customDistZip')
        and:
        file('customName.zip').usingNativeTools().unzipTo(file("unzip"))
        file("unzip/someFile").assertIsFile()
    }


    def createTaskForCustomDistributionWithEmptyCustomName() {
        when:
        buildFile << """
            apply plugin:'distribution'
            distributions {
                custom{
                    baseName=''
                    contents {
                        from { "someFile" }
                    }
                }
            }


            """
        then:
        runAndFail('customDistZip')
        failure.assertThatDescription(containsString("Distribution baseName must not be null or empty! Check your configuration of the distribution plugin."))
    }



    def createDistributionWithoutVersion() {
        given:
        createDir('src/main/dist') {
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
            }
        }
        and:
        buildFile << """
            apply plugin:'distribution'


            distributions {
                main{
                    baseName='myDistribution'
                }
            }
            """
        when:
        run('distZip')
        then:
        file('myDistribution.zip').exists()
    }

    def createDistributionWithVersion() {
        given:
        createDir('src/main/dist') {
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
            }
        }
        and:
        buildFile << """
            apply plugin:'distribution'

            version = '1.2'
            distributions {
                main{
                    baseName='myDistribution'
                }
            }
            distZip{

            }
            """
        when:
        run('distZip')
        then:
        file('myDistribution-1.2.zip').exists()
    }

    def createDistributionWithoutBaseNameAndVersion() {
        given:
        createDir('src/main/dist') {
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
            }
        }
        and:
        buildFile << """
            apply plugin:'distribution'

            """
        when:
        run('distZip')
        then:
        file('TestProject.zip').exists()
    }

    def createDistributionWithoutBaseNameAndWithVersion() {
        given:
        createDir('src/main/dist') {
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
            }
        }
        and:
        buildFile << """
            apply plugin:'distribution'

            version = 1.2
            """
        when:
        run('distZip')
        then:
        file('TestProject-1.2.zip').exists()
    }

    def createCreateArchiveForCustomDistribution(){
        given:
        createDir('src/main/custom') {
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
            }
        }
        and:
        buildFile << """
            apply plugin:'distribution'

            distributions{
                custom
            }
            """
        when:
        run('customDistZip')
        then:
        file('TestProject-custom.zip').exists()
    }


    def includeFileFromSrcMainCustom() {
        given:
        createDir('src/main/custom'){
            file 'file1.txt'
            dir {
                file 'file2.txt'
            }
        }
        and:
        buildFile << """
            apply plugin:'distribution'

            version = 1.2

            distributions{
                custom
            }
            """
        when:
        run('customDistZip')
        then:
        file('TestProject-custom-1.2.zip').exists()
        file('TestProject-custom-1.2.zip').usingNativeTools().unzipTo(file("unzip"))
        file("unzip").assertHasDescendants('file1.txt','dir/file2.txt')
    }

    def includeFileFromDistContent() {
        given:
        createDir('src/main/custom'){
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
            apply plugin:'distribution'

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
        file('TestProject-custom-1.2.zip').exists()
        file('TestProject-custom-1.2.zip').usingNativeTools().unzipTo(file("unzip"))
        file("unzip").assertHasDescendants('file1.txt','dir/file2.txt','docs/file3.txt','docs/dir2/file4.txt')
    }

    def installFromDistContent() {
        given:
        createDir('src/main/custom'){
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
            apply plugin:'distribution'

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
        run('installcustomDist')
        then:
        file('build/install/TestProject-custom').exists()
        file('build/install/TestProject-custom').assertHasDescendants('docs/file3.txt','docs/dir2/file4.txt')
    }

    def createTarTaskForCustomDistribution() {
        when:
        buildFile << """
            apply plugin:'distribution'

            distributions {
                custom{
                    contents {
                        from { "someFile" }
                    }
                }
            }

            """
        then:
        succeeds('customDistTar')
        and:
        file('TestProject-custom.tar').usingNativeTools().untarTo(file("untar"))
        file("untar/someFile").assertIsFile()
    }

}
