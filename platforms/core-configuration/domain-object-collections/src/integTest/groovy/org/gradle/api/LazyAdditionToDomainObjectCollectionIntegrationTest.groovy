/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.TaskOrderSpecs

class LazyAdditionToDomainObjectCollectionIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            abstract class Base {}
            abstract class Sub extends Base {}

            def container = objects.domainObjectSet(Base)

            def create(type) {
                def ret = objects.newInstance(type)
                println "created " + ret
                return ret
            }
        """
    }

    def "addLater(Base) triggers configuration with eager all"() {
        buildFile << """
            container.all {
                println "all called on " + it
            }
            container.addLater(provider { create(Base) })
        """
        expect:
        succeeds("help")
        outputContains("all called on ")
    }
    def "addLater(Base) triggers configuration with eager withType(Base)"() {
        buildFile << """
            container.withType(Base) {
                println "withType(Base) called on " + it
            }
            container.addLater(provider { create(Base) })
        """
        expect:
        succeeds("help")
        outputContains("withType(Base) called on ")
    }
    def "addLater(Base) triggers configuration with eager withType(Sub)"() {
        buildFile << """
            container.withType(Sub) {
                println "withType(Sub) called on " + it
            }
            container.addLater(provider { create(Base) })
        """
        expect:
        succeeds("help")
        outputDoesNotContain("withType(Sub) called on")
    }

    def "addLater(Sub) triggers configuration with eager all"() {
        buildFile << """
            container.all {
                println "all called on " + it
            }
            container.addLater(provider { create(Sub) })
        """
        expect:
        succeeds("help")
        outputContains("all called on ")
    }
    def "addLater(Sub) triggers configuration with eager withType(Base)"() {
        buildFile << """
            container.withType(Base) {
                println "withType(Base) called on " + it
            }
            container.addLater(provider { create(Sub) })
        """
        expect:
        succeeds("help")
        outputContains("withType(Base) called on ")
    }
    def "addLater(Sub) triggers configuration with eager withType(Sub)"() {
        buildFile << """
            container.withType(Sub) {
                println "withType(Sub) called on " + it
            }
            container.addLater(provider { create(Sub) })
        """
        expect:
        succeeds("help")
        outputContains("withType(Sub) called on")
    }

    def "addAllLater(Base) triggers configuration with eager all"() {
        buildFile << """
            container.all {
                println "all called on " + it
            }
            def list = objects.listProperty(Base)
            list.addAll(create(Base))
            container.addAllLater(list)
        """
        expect:
        succeeds("help")
        outputContains("all called on ")
    }
    def "addAllLater(Base) triggers configuration with eager withType(Base)"() {
        buildFile << """
            container.withType(Base) {
                println "withType(Base) called on " + it
            }
            def list = objects.listProperty(Base)
            list.addAll(create(Base))
            container.addAllLater(list)
        """
        expect:
        succeeds("help")
        outputContains("withType(Base) called on ")
    }
    def "addAllLater(Base) triggers configuration with eager withType(Sub)"() {
        buildFile << """
            container.withType(Sub) {
                println "withType(Sub) called on " + it
            }
            def list = objects.listProperty(Base)
            list.addAll(create(Base))
            container.addAllLater(list)
        """
        expect:
        succeeds("help")
        outputDoesNotContain("withType(Sub) called on")
    }

    def "addAllLater(Sub) triggers configuration with eager all"() {
        buildFile << """
            container.all {
                println "all called on " + it
            }
            def list = objects.listProperty(Sub)
            list.addAll(create(Sub))
            container.addAllLater(list)
        """
        expect:
        succeeds("help")
        outputContains("all called on ")
    }
    def "addAllLater(Sub) triggers configuration with eager withType(Base)"() {
        buildFile << """
            container.withType(Base) {
                println "withType(Base) called on " + it
            }
            def list = objects.listProperty(Sub)
            list.addAll(create(Sub))
            container.addAllLater(list)
        """
        expect:
        succeeds("help")
        outputContains("withType(Base) called on ")
    }
    def "addAllLater(Sub) triggers configuration with eager withType(Sub)"() {
        buildFile << """
            container.withType(Sub) {
                println "withType(Sub) called on " + it
            }
            def list = objects.listProperty(Sub)
            list.addAll(create(Sub))
            container.addAllLater(list)
        """
        expect:
        succeeds("help")
        outputContains("withType(Sub) called on")
    }

    def "addAllLater(Base, Sub) triggers configuration with eager all"() {
        buildFile << """
            container.all {
                println "all called on " + it
            }
            def list = objects.listProperty(Base)
            list.addAll(create(Base), create(Sub))
            container.addAllLater(list)
        """
        expect:
        succeeds("help")
        outputContains("all called on ")
    }
    def "addAllLater(Base, Sub) triggers configuration with eager withType(Base)"() {
        buildFile << """
            container.withType(Base) {
                println "withType(Base) called on " + it
            }
            def list = objects.listProperty(Base)
            list.addAll(create(Base), create(Sub))
            container.addAllLater(list)
        """
        expect:
        succeeds("help")
        outputContains("withType(Base) called on ")
    }

    @NotYetImplemented
    def "addAllLater(Base, Sub) triggers configuration with eager withType(Sub)"() {
        buildFile << """
            container.withType(Sub) {
                println "withType(Sub) called on " + it
            }
            def list = objects.listProperty(Base)
            list.addAll(create(Base), create(Sub))
            container.addAllLater(list)
        """
        expect:
        succeeds("help")
        outputContains("withType(Sub) called on")
    }

    def "addAllLater(Base, Sub) accepts non-collection provider types with eager all"() {
        buildFile << """
            container.all {
                println "all called on " + it
            }
            container.addAllLater(provider { [create(Base), create(Sub)] })
        """
        expect:
        succeeds("help")
        outputContains("all called on")
    }

    def "addAllLater(Base, Sub) accepts non-collection provider types with eager withType(Base)"() {
        buildFile << """
            container.withType(Base) {
                println "withType(Base) called on " + it
            }
            container.addAllLater(provider { [create(Base), create(Sub)] })
        """
        expect:
        succeeds("help")
        outputContains("withType(Base) called on")
    }

    def "elements carries task dependencies"() {
        buildFile.text = """
            class NamedThing implements Named {
                public String name
                public NamedThing(String name) {
                    this.name = name
                }
                public String getName() {
                    return name
                }
                public String toString() {
                    return name
                }
            }

            def task1 = tasks.register("task1") {
                outputs.file("a.txt")
                outputs.file("b.txt")
            }
            def task2 = tasks.register("task2") {
                outputs.file("c.txt")
            }

            def container = objects.$containerType(NamedThing)
            container.addAllLater(task1.flatMap { it.outputs.files.elements }.map { it.collect { new NamedThing(it.asFile.name) } })
            container.addAllLater(task2.flatMap { it.outputs.files.elements }.map { it.collect { new NamedThing(it.asFile.name) } })
            def elements = container.elements

            def task3 = tasks.register("task3") {
                inputs.property("elements", elements)
                doLast {
                    println(elements.get())
                }
            }
        """

        when:
        succeeds(":task3")

        then:
        outputContains("[a.txt, b.txt, c.txt]")
        result.assertTaskOrder(TaskOrderSpecs.any(":task1", ":task2"), ":task3")

        where:
        containerType << [
            "domainObjectContainer",
            "polymorphicDomainObjectContainer",
            "domainObjectSet",
            "namedDomainObjectSet",
            "namedDomainObjectList"
        ]
    }

}
