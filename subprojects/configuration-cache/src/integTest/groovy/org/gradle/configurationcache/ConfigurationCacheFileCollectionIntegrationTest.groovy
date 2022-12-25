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
    def "directory tree is treated as build input when its contents are queried during configuration"() {
        buildFile << """
            task report {
                def tree = fileTree("src")
                def file1 = file("src/file1")
                def result = $expression
                doLast {
                    println(result)
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
        outputContains(output1)

        when:
        configurationCacheRun("report")

        then:
        fixture.assertStateLoaded()
        outputContains(output1)

        when: // a file is added
        dir.createFile("file3")
        configurationCacheRun("report")

        then:
        fixture.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because an input to build file 'build.gradle' has changed.")
        outputContains(output2)

        when:
        configurationCacheRun("report")

        then:
        fixture.assertStateLoaded()
        outputContains(output2)

        when: // a file is removed
        dir.file("file1").delete()
        configurationCacheRun("report")

        then:
        fixture.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because an input to build file 'build.gradle' has changed.")
        outputContains(output3)

        where:
        expression                                                                                      | output1                     | output2                            | output3
        "'files=' + tree.files.name.sort()"                                                             | "files=[file1, file2]"      | "files=[file1, file2, file3]"      | "files=[file2, file3]"
        "'files=' + { def names = new TreeSet(); tree.visit { d -> names.add(d.file.name) }; names }()" | "files=[dir, file1, file2]" | "files=[dir, file1, file2, file3]" | "files=[dir, file2, file3]"
        // TODO - should not invalidate the cache for this expression
        "'empty=' + tree.empty"                                                                         | "empty=false"               | "empty=false"                      | "empty=false"
        // TODO - should not invalidate the cache for this expression until the file is removed
        "'contains=' + tree.contains(file1)"                                                            | "contains=true"             | "contains=true"                    | "contains=false"
    }

    def "missing directory tree is treated as build input when its contents are queried during configuration"() {
        buildFile << """
            task report {
                def tree = fileTree("src")
                def file1 = file("src/file1")
                def result = $expression
                doLast {
                    println(result)
                }
            }
        """
        def dir = file("src")
        def fixture = newConfigurationCacheFixture()

        when:
        configurationCacheRun("report")

        then:
        fixture.assertStateStored()
        outputContains(output1)

        when:
        configurationCacheRun("report")

        then:
        fixture.assertStateLoaded()
        outputContains(output1)

        when:
        dir.createDir() // empty directory
        configurationCacheRun("report")

        then:
        fixture.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because an input to build file 'build.gradle' has changed.")
        outputContains(output1)

        when:
        configurationCacheRun("report")

        then:
        fixture.assertStateLoaded()
        outputContains(output1)

        when: // file created
        dir.file("file1").createFile()
        configurationCacheRun("report")

        then:
        fixture.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because an input to build file 'build.gradle' has changed.")
        outputContains(output2)

        when:
        configurationCacheRun("report")

        then:
        fixture.assertStateLoaded()
        outputContains(output2)

        when:
        dir.deleteDir() // directory does not exist
        configurationCacheRun("report")

        then:
        fixture.assertStateStored() // TODO - it would be good to reuse a previous entry here
        outputContains("Calculating task graph as configuration cache cannot be reused because an input to build file 'build.gradle' has changed.")
        outputContains(output1)

        where:
        expression                                                                                      | output1          | output2
        // TODO - should not invalidate the cache when the empty root directory is created
        "'files=' + tree.files.name.sort()"                                                             | "files=[]"       | "files=[file1]"
        "'files=' + { def names = new TreeSet(); tree.visit { d -> names.add(d.file.name) }; names }()" | "files=[]"       | "files=[file1]"
        // TODO - should not invalidate the cache when the empty root directory is created
        "'empty=' + tree.empty"                                                                         | "empty=true"     | "empty=false"
        // TODO - should not invalidate the cache when the empty root directory is created
        "'contains=' + tree.contains(file1)"                                                            | "contains=false" | "contains=true"
    }

    def "elements of fixed file collection are not treated as build inputs"() {
        buildFile << """
            task report {
                def files = files("file1", "file2")
                def file1 = file("file1")
                def result = $expression
                doLast {
                    println(result)
                }
            }
        """
        def file1 = file("file1").createFile()
        def file2 = file("file2").createFile()
        def fixture = newConfigurationCacheFixture()

        when:
        configurationCacheRun("report")

        then:
        fixture.assertStateStored()
        outputContains(output)

        when:
        configurationCacheRun("report")

        then:
        fixture.assertStateLoaded()
        outputContains(output)

        when: // change file contents
        file1.text = "updated"
        configurationCacheRun("report")

        then:
        fixture.assertStateLoaded()
        outputContains(output)

        where:
        expression              | output
        "files.files.name"      | "[file1, file2]"
        "files.empty"           | "false"
        "files.contains(file1)" | "true"
    }
}
