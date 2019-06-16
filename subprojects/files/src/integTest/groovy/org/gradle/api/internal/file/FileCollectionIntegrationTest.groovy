/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.file

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
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

    def "finalized file collection resolves locations and ignores later changes to source paths"() {
        buildFile << """
            def files = objects.fileCollection()
            def name = 'a'
            files.from { name }
            
            def names = ['b', 'c']
            files.from(names)

            files.finalizeValue()
            name = 'ignore-me'
            names.clear()
            
            assert files.files as List == [file('a'), file('b'), file('c')]
        """

        expect:
        succeeds()
    }

    def "finalized file collection ignores later changes to nested file collections"() {
        buildFile << """
            def nested = objects.fileCollection()
            def name = 'a'
            nested.from { name }
            
            def names = ['b', 'c']
            nested.from(names)

            def files = objects.fileCollection()
            files.from(nested)
            files.finalizeValue()
            name = 'ignore-me'
            names.clear()
            nested.from('ignore-me')
            
            assert files.files as List == [file('a'), file('b'), file('c')]
        """

        expect:
        succeeds()
    }

    def "finalized file collection still reflects changes to file system but not changes to locations"() {
        buildFile << """
            def files = objects.fileCollection()
            def name = 'a'
            files.from { 
                fileTree({ name }) {
                    include '**/*.txt'
                }
            }

            files.finalizeValue()
            name = 'b'
            
            assert files.files.empty
            
            file('a').mkdirs()
            def f1 = file('a/thing.txt')
            f1.text = 'thing'
            
            assert files.files as List == [f1]
        """

        expect:
        succeeds()
    }

    def "cannot mutate finalized file collection"() {
        buildFile << """
            def files = objects.fileCollection()
            files.finalizeValue()
            
            task broken {
                doLast {
                    files.from('bad')
                }
            }
        """

        when:
        fails('broken')

        then:
        failure.assertHasCause("The value for file collection is final and cannot be changed.")
    }
}
