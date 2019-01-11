/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.integtests.fixtures.executer.TaskOrderSpecs.any
import static org.gradle.integtests.fixtures.executer.TaskOrderSpecs.exact

class DeleteTaskIntegrationTest extends AbstractIntegrationSpec {
    def "delete task removes specified files"() {
        file('foo') << "foo"
        file('bar') << "bar"
        file('baz') << "baz"

        buildFile << """
            assert file('foo').exists()
            assert file('bar').exists()
            assert file('baz').exists()
            
            task clean(type: Delete) {
                delete 'foo'
                delete file('bar')
                delete files('baz')
             }
        """

        when:
        succeeds "clean"

        then:
        !file('foo').exists()
        !file('bar').exists()
        !file('baz').exists()
    }

    def "deleted files show up in task destroys"() {
        buildFile << """
            import org.gradle.api.internal.tasks.properties.PropertyVisitor
            import org.gradle.api.internal.tasks.properties.PropertyWalker
            import org.gradle.internal.file.PathToFileResolver
            import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection
            import org.gradle.api.internal.tasks.TaskPropertyUtils
            
            task clean(type: Delete) {
                delete 'foo'
                delete file('bar')
                delete files('baz')
                

                doLast {
                    def destroyablePaths = []
                    def resolver = project.services.get(PathToFileResolver)
                    def propertyWalker = project.services.get(PropertyWalker)
                    TaskPropertyUtils.visitProperties(propertyWalker, it, new PropertyVisitor.Adapter() {
                        void visitDestroyableProperty(Object value) {
                            destroyablePaths << value
                        }
                    })
                    def destroyableFiles = new DefaultConfigurableFileCollection(resolver, null, destroyablePaths).files 
                    assert destroyableFiles.size() == 3 &&
                        destroyableFiles.containsAll([
                            file('foo'), 
                            file('bar'), 
                            file('baz')
                        ])
                }
             }
        """

        expect:
        succeeds "clean"
    }

    def "clean build and build clean work reliably"() {
        settingsFile << "include 'a', 'b'"
        buildFile << """
            subprojects {
                apply plugin: 'java'
            }
            project(':b') {
                dependencies {
                    compile project(':a')
                }
            }
        """

        file("a/src/main/java/Foo.java") << "public class Foo {}"
        file("b/src/main/java/Bar.java") << "public class Bar extends Foo {}"

        when:
        args "--parallel"
        succeeds 'clean', 'build'

        then:
        result.assertTaskOrder(
            any(
                exact(':a:clean', ':a:compileJava'),
                exact(':b:clean', ':b:compileJava'),
                exact(':a:compileJava', ':b:compileJava')
            )
        )

        when:
        args "--parallel"
        succeeds 'build', 'clean'

        then:
        result.assertTaskOrder(
            exact(':a:compileJava', ':b:compileJava'),
            any(':a:clean', ':b:clean')
        )
    }
}
