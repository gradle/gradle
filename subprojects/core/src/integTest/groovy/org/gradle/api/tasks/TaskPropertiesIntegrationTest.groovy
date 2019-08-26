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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskPropertiesIntegrationTest extends AbstractIntegrationSpec {
    def "can define task with abstract read-only Property<T> property"() {
        given:
        buildFile << """
            abstract class MyTask extends DefaultTask {
                abstract Property<Integer> getCount()
                
                @TaskAction
                void go() {
                    println("count = \${count.get()}")
                }
            }
            
            tasks.create("thing", MyTask) {
                println("property = \$count")
                count = 12
            }
        """

        when:
        succeeds("thing")

        then:
        outputContains("property = task ':thing' property 'count'")
        outputContains("count = 12")
    }

    def "reports failure to query managed Property<T> with no value"() {
        given:
        buildFile << """
            abstract class MyTask extends DefaultTask {
                abstract Property<Integer> getCount()
                
                @TaskAction
                void go() {
                    println("count = \${count.get()}")
                }
            }
            
            tasks.create("thing", MyTask) {
            }
        """

        when:
        fails("thing")

        then:
        failure.assertHasCause("No value has been specified for task ':thing' property 'count'")
    }

    def "reports failure to query unmanaged Property<T> with no value"() {
        given:
        buildFile << """
            abstract class MyTask extends DefaultTask {
                final Property<Integer> count = project.objects.property(Integer)
                
                @TaskAction
                void go() {
                    println("count = \${count.get()}")
                }
            }
            
            tasks.create("thing", MyTask) {
                println("property = \$count")
            }
        """

        when:
        fails("thing")

        then:
        outputContains("property = task ':thing' property 'count'")
        failure.assertHasCause("No value has been specified for task ':thing' property 'count'")
    }

    def "can define task with abstract read-only ConfigurableFileCollection property"() {
        given:
        buildFile << """
            abstract class MyTask extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getSource()
                
                @TaskAction
                void go() {
                    println("files = \${source.files.name}")
                }
            }
            
            tasks.create("thing", MyTask) {
                source.from("a", "b", "c")
            }
        """

        when:
        succeeds("thing")

        then:
        outputContains("files = [a, b, c]")
    }

}
