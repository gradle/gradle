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
        buildFile << """
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
        file('file/b/other.txt').createFile() // not an input
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
        buildFile << """
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
        file('file/b/other.txt').createFile() // not an input
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
        file('files/other/add-three.txt').createFile()
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
}
