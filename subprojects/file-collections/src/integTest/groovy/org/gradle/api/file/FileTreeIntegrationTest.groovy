/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.file

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class FileTreeIntegrationTest extends AbstractIntegrationSpec {
    def "can subtract the elements of another tree"() {
        given:
        file('files/one.txt').createFile()
        file('files/a/two.txt').createFile()
        file('files/b/ignore.txt').createFile()
        buildFile """
            def files = fileTree(dir: 'files').minus(fileTree(dir: 'files/b'))
            task copy(type: Copy) {
                from files
                into 'dest'
            }
        """

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'one.txt',
            'two.txt' // does not preserve structure, but probably should
        )

        when:
        file('files/b/other.txt').createFile() // not an input
        run 'copy'

        then:
        result.assertTaskSkipped(':copy')
        file('dest').assertHasDescendants(
            'one.txt',
            'two.txt'
        )

        when:
        file('files/a/three.txt').createFile()
        run 'copy'

        then:
        result.assertTaskNotSkipped(':copy')
        file('dest').assertHasDescendants(
            'one.txt',
            'two.txt',
            'three.txt'
        )
    }

    def "can add files to the result of subtracting the elements of another tree"() {
        given:
        file('files/one.txt').createFile()
        file('files/a/two.txt').createFile()
        file('files/b/ignore.txt').createFile()
        file('other/add-one.txt').createFile()
        file('other/a/add-two.txt').createFile()
        buildFile """
            def files = fileTree(dir: 'files').minus(fileTree(dir: 'files/b')).plus(fileTree(dir: 'other'))
            task copy(type: Copy) {
                from files
                into 'dest'
            }
        """

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'one.txt',
            'two.txt',
            'add-one.txt',
            'a/add-two.txt'
        )

        when:
        file('files/b/other.txt').createFile() // not an input
        run 'copy'

        then:
        result.assertTaskSkipped(':copy')
        file('dest').assertHasDescendants(
            'one.txt',
            'two.txt',
            'add-one.txt',
            'a/add-two.txt'
        )

        when:
        file('files/a/three.txt').createFile()
        file('other/add-three.txt').createFile()
        run 'copy'

        then:
        result.assertTaskNotSkipped(':copy')
        file('dest').assertHasDescendants(
            'one.txt',
            'two.txt',
            'three.txt',
            'add-one.txt',
            'a/add-two.txt',
            'add-three.txt'
        )
    }

    def "can filter the elements of a tree using a closure that receives a File"() {
        given:
        file('files/one.txt').createFile()
        file('files/a/two.txt').createFile()
        file('files/b/ignore.txt').createFile()
        file('other/other-one.txt').createFile()
        file('other/a/other-ignore.txt').createFile()
        buildFile """
            def files = fileTree(dir: 'files').plus(fileTree(dir: 'other')).filter {
                println("checking \${it.name}")
                !it.name.contains('ignore')
            }
            task copy(type: Copy) {
                from files
                into 'dest'
            }
        """

        when:
        run 'copy'

        then:
        outputContains("checking one.txt")
        outputContains("checking ignore.txt")
        file('dest').assertHasDescendants(
            'one.txt',
            'two.txt',
            'other-one.txt'
        )

        when:
        file('files/a/more-ignore.txt').createFile() // not an input
        run 'copy'

        then:
        outputContains("checking one.txt")
        outputContains("checking ignore.txt")
        outputContains("checking more-ignore.txt")
        result.assertTaskSkipped(':copy')
        file('dest').assertHasDescendants(
            'one.txt',
            'two.txt',
            'other-one.txt'
        )

        when:
        file('files/a/three.txt').createFile()
        file('other/add-three.txt').createFile()
        run 'copy'

        then:
        outputContains("checking one.txt")
        outputContains("checking ignore.txt")
        outputContains("checking three.txt")
        result.assertTaskNotSkipped(':copy')
        file('dest').assertHasDescendants(
            'one.txt',
            'two.txt',
            'three.txt',
            'other-one.txt',
            'add-three.txt'
        )
    }

    def "can filter the elements of a tree using a closure that receives pattern set"() {
        given:
        file('files/one.txt').createFile()
        file('files/a/two.txt').createFile()
        file('files/a/IGNORE.txt').createFile()
        file('files/b/ignore.txt').createFile()
        file('files/b/one.bin').createFile()
        file('files/b/wrong case.TXT').createFile()
        file('other/c/other-one.txt').createFile()
        file('other/c/other-ignore.txt').createFile()
        buildFile """
            def files = files('files', 'other').asFileTree.matching {
                include("**/*.txt")
                exclude("**/*ignore*")
            }
            task copy(type: Copy) {
                from files
                into 'dest'
                includeEmptyDirs = false
            }
        """

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'one.txt',
            'a/two.txt',
            'a/IGNORE.txt', // exclude patterns are case sensitive by default
            'c/other-one.txt'
        )

        when:
        file('files/a/more-ignore.txt').createFile() // not an input
        file('files/a/more.TXT').createFile() // not an input
        run 'copy'

        then:
        result.assertTaskSkipped(':copy')
        file('dest').assertHasDescendants(
            'one.txt',
            'a/two.txt',
            'a/IGNORE.txt',
            'c/other-one.txt'
        )

        when:
        file('files/a/three.txt').createFile()
        file('other/add-three.txt').createFile()
        run 'copy'

        then:
        result.assertTaskNotSkipped(':copy')
        file('dest').assertHasDescendants(
            'one.txt',
            'a/two.txt',
            'a/IGNORE.txt',
            'a/three.txt',
            'c/other-one.txt',
            'add-three.txt'
        )
    }
}
