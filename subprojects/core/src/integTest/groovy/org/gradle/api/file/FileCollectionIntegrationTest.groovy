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

package org.gradle.api.file

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue
import spock.lang.Unroll

class FileCollectionIntegrationTest extends AbstractIntegrationSpec {
    @Unroll
    def "can use 'as' operator with #type"() {
        buildFile << """
            def fileCollection = files("input.txt")
            def castValue = fileCollection as $type
            println "Cast value: \$castValue (\${castValue.getClass().name})"
            assert castValue instanceof $type
        """

        expect:
        succeeds "help"

        where:
        type << ["Object", "Object[]", "Set", "LinkedHashSet", "List", "LinkedList", "Collection", "FileCollection"]
    }

    @Issue("gradle/gradle#1108")
    def "can use 'subtree()' to re-root zipTrees"() {
        given:
        createZip('test.zip') {
            subdir1 {
                file 'file1.txt'
            }
            subdir2 {
                file 'file2.txt'
            }
        }
        and:
        buildFile << '''
            task copy(type: Copy) {
                from zipTree('test.zip').subTree('subdir1')
                into 'dest'
            }
            '''
        when:
        run 'copy'
        then:
        file('dest').assertHasDescendants('file1.txt')
    }
}
