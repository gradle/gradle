/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache

class ConfigurationCacheFileCollectionIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    def "directory tree is treated as build input when queried during configuration"() {
        buildFile << """
            task report {
                def tree = fileTree("src")
                def files = tree.files.name.sort()
                doLast {
                    println("files=" + files)
                }
            }
        """
        def dir = createDir("src") {
            file("file1")
            dir("dir") {
                file("file2")
            }
        }
        def fixture = newConfigurationCacheFixture()

        when:
        configurationCacheRun("report")

        then:
        fixture.assertStateStored()
        outputContains("files=[file1, file2]")

        when:
        configurationCacheRun("report")

        then:
        fixture.assertStateLoaded()
        outputContains("files=[file1, file2]")

        when: // a file is added
        dir.createFile("file3")
        configurationCacheRun("report")

        then:
        fixture.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because an input to build file 'build.gradle' has changed.")
        outputContains("files=[file1, file2, file3]")

        when:
        configurationCacheRun("report")

        then:
        fixture.assertStateLoaded()
        outputContains("files=[file1, file2, file3]")

        when: // a file is removed
        dir.file("file1").delete()
        configurationCacheRun("report")

        then:
        fixture.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because an input to build file 'build.gradle' has changed.")
        outputContains("files=[file2, file3]")
    }

    def "missing directory tree is treated as build input when queried during configuration"() {
        buildFile << """
            task report {
                def tree = fileTree("src")
                def files = tree.files.name.sort()
                doLast {
                    println("files=" + files)
                }
            }
        """
        def dir = file("src")
        def fixture = newConfigurationCacheFixture()

        when:
        configurationCacheRun("report")

        then:
        fixture.assertStateStored()
        outputContains("files=[]")

        when:
        configurationCacheRun("report")

        then:
        fixture.assertStateLoaded()
        outputContains("files=[]")

        when:
        dir.createDir() // empty directory
        configurationCacheRun("report")

        then:
        fixture.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because an input to build file 'build.gradle' has changed.")
        outputContains("files=[]")

        when:
        configurationCacheRun("report")

        then:
        fixture.assertStateLoaded()
        outputContains("files=[]")

        when: // file created
        dir.file("dir/file1").createFile()
        configurationCacheRun("report")

        then:
        fixture.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because an input to build file 'build.gradle' has changed.")
        outputContains("files=[file1]")

        when:
        configurationCacheRun("report")

        then:
        fixture.assertStateLoaded()
        outputContains("files=[file1]")

        when:
        dir.deleteDir() // directory does not exist
        configurationCacheRun("report")

        then:
        fixture.assertStateStored() // It would be good to reuse a previous entry here
        outputContains("Calculating task graph as configuration cache cannot be reused because an input to build file 'build.gradle' has changed.")
        outputContains("files=[]")
    }
}
