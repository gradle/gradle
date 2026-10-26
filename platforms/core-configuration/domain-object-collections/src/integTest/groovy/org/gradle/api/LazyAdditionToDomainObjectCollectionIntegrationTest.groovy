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
import spock.lang.Issue

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

    def "values of provider added with addAllLater to #factory are captured when first read"() {
        buildFile """
            ${namedThing}

            def files = objects.fileCollection()
            files.from("a")
            def collection = objects.${factory}(NamedThing)
            collection.addAllLater(files.elements.map { it.collect { new NamedThing(it.asFile.name) } })

            println "first = " + collection.toList()
            files.from("b")
            println "second = " + collection.toList()
            println "size = " + collection.size()
            println "isEmpty = " + collection.isEmpty()
        """

        expect:
        succeeds("help")
        outputContains("first = [a]")
        outputContains("second = [a]")
        outputContains("size = 1")
        outputContains("isEmpty = false")

        where:
        factory << ["domainObjectSet", "namedDomainObjectSet", "namedDomainObjectList"]
    }

    def "size of #factory matches what a read would return before provider added with addAllLater is read"() {
        buildFile """
            ${namedThing}

            def files = objects.fileCollection()
            files.from("a")
            def collection = objects.${factory}(NamedThing)
            collection.addAllLater(files.elements.map { it.collect { new NamedThing(it.asFile.name) } })

            println "size = " + collection.size()
            files.from("b")
            println "size after change = " + collection.size()
            println "read = " + collection.toList()
            files.from("c")
            println "size after read = " + collection.size()
        """

        expect:
        succeeds("help")
        outputContains("size = 1")
        outputContains("size after change = 2")
        outputContains("read = [a, b]")
        outputContains("size after read = 2")

        where:
        factory << ["domainObjectSet", "namedDomainObjectSet", "namedDomainObjectList"]
    }

    def "size and emptiness of #factory match iteration when provider added with addAllLater becomes empty"() {
        buildFile """
            ${namedThing}

            def names = objects.listProperty(String)
            names.add("a")
            def collection = objects.${factory}(NamedThing)
            collection.addAllLater(names.map { it.collect { new NamedThing(it) } })

            println "read = " + collection.toList()
            names.set([])
            println "after change = " + collection.toList() + ", size = " + collection.size() + ", isEmpty = " + collection.isEmpty()

            def empty = objects.${factory}(NamedThing)
            empty.addAllLater(provider { [] })
            println "empty = " + empty.toList() + ", size = " + empty.size() + ", isEmpty = " + empty.isEmpty()
        """

        expect:
        succeeds("help")
        outputContains("read = [a]")
        outputContains("after change = [a], size = 1, isEmpty = false")
        outputContains("empty = [], size = 0, isEmpty = true")

        where:
        factory << ["domainObjectSet", "namedDomainObjectSet", "namedDomainObjectList"]
    }

    def "size and emptiness of #factory match iteration when provider added with addAllLater has no value"() {
        buildFile """
            ${namedThing}

            def collection = objects.${factory}(NamedThing)
            collection.addAllLater(provider { null })

            println "before read: size = " + collection.size() + ", isEmpty = " + collection.isEmpty() + ", filtered isEmpty = " + collection.withType(NamedThing).isEmpty()
            println "read = " + collection.toList() + ", size = " + collection.size() + ", isEmpty = " + collection.isEmpty()
        """

        expect:
        succeeds("help")
        outputContains("before read: size = 0, isEmpty = true, filtered isEmpty = true")
        outputContains("read = [], size = 0, isEmpty = true")

        where:
        factory << ["domainObjectSet", "namedDomainObjectSet", "namedDomainObjectList"]
    }

    def "can configure elements of subtype of #factory when provider added with addAllLater has no value"() {
        buildFile """
            ${namedThing}
            class SubThing extends NamedThing {
                SubThing(String name) {
                    super(name)
                }
            }

            def collection = objects.${factory}(NamedThing)
            collection.add(new SubThing("s"))
            collection.addAllLater(provider { null })

            collection.withType(SubThing) { println "configured " + it }
            println "read = " + collection.toList()
        """

        expect:
        succeeds("help")
        outputContains("configured s")
        outputContains("read = [s]")

        where:
        factory << ["domainObjectSet", "namedDomainObjectSet", "namedDomainObjectList"]
    }

    def "size of domain object set does not count duplicates of values added with addAllLater"() {
        buildFile """
            def set = objects.domainObjectSet(String)
            set.add("a")
            set.addAllLater(provider { ["a", "c"] })

            println "size before read = " + set.size()
            println "read = " + set.toList() + ", size = " + set.size()
        """

        expect:
        succeeds("help")
        outputContains("size before read = 2")
        outputContains("read = [a, c], size = 2")
    }

    @Issue("https://github.com/gradle/gradle/issues/33152")
    def "can stream domain object set with #description"() {
        buildFile """
            def set = objects.domainObjectSet(String)
            ${addition}

            println "stream = " + set.stream().toList() + ", size = " + set.size() + ", isEmpty = " + set.isEmpty()
        """

        expect:
        succeeds("help")
        outputContains("stream = ${expected}, size = ${expected.size()}, isEmpty = ${expected.isEmpty()}")

        where:
        description              | addition                                                 | expected
        "addLater duplicate"     | 'set.add("a"); set.addLater(provider { "a" })'           | ["a"]
        "addLater without value" | 'set.addLater(provider { null })'                        | []
        "addAllLater duplicate"  | 'set.add("a"); set.addAllLater(provider { ["a", "b"] })' | ["a", "b"]
    }

    def "removing value provided with addAllLater keeps duplicates in domain object set hidden"() {
        buildFile """
            def set = objects.domainObjectSet(String)
            set.add("b")
            set.addAllLater(provider { ["a", "b"] })

            println "read = " + set.toList()
            set.remove("a")
            println "after remove = " + set.toList() + ", size = " + set.size()
        """

        expect:
        succeeds("help")
        outputContains("read = [b, a]")
        outputContains("after remove = [b], size = 1")
    }

    def "eager action registered before #method on #factory is executed once on the element kept in the collection"() {
        buildFile """
            ${namedThing}

            def configured = []
            def collection = objects.${factory}(NamedThing)
            collection.all { configured << it }
            if ("${method}" == "addLater") {
                collection.addLater(provider { new NamedThing("x") })
            } else {
                collection.addAllLater(provider { [new NamedThing("x")] })
            }

            def read = collection.toList()
            println "configured = " + configured.size() + ", read = " + read + ", same instance = " + configured[0].is(read[0])
        """

        expect:
        succeeds("help")
        outputContains("configured = 1, read = [x], same instance = true")

        where:
        factory << ["domainObjectSet", "namedDomainObjectSet", "namedDomainObjectList"]
        combined:
        method << ["addLater", "addAllLater"]
    }

    @Issue("https://github.com/gradle/gradle/issues/23904")
    def "values of provider added with addAllLater are captured once when #description"() {
        file("a.txt").text = "a"
        file("b.txt").text = "b"
        buildFile """
            def files = objects.fileCollection()
            def setOne = objects.domainObjectSet(String)
            def setTwo = objects.domainObjectSet(String)
            files.from("a.txt")

            def addAllLater = { setOne.addAllLater(files.elements.map { it.collect { f -> f.asFile.name + "_once" } }) }
            def addAll = { setOne.all { println "all called for " + it; setTwo.add(it + "_twice") } }
            if (${addAllLaterFirst}) {
                addAllLater()
                addAll()
            } else {
                addAll()
                addAllLater()
            }

            files.from("b.txt")
            println "setOne = " + setOne.toList()
            println "setTwo = " + setTwo.toList()
        """

        expect:
        succeeds("help")
        output.count("all called for a.txt_once") == 1
        outputDoesNotContain("all called for b.txt_once")
        outputContains("setOne = [a.txt_once]")
        outputContains("setTwo = [a.txt_once_twice]")

        where:
        description                     | addAllLaterFirst
        "eager action registered first" | false
        "addAllLater called first"      | true
    }

    def getNamedThing() {
        """
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
        """
    }

    def "elements carries task dependencies"() {
        buildFile.text = """
            ${namedThing}

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

    def "elements of filtered collection may contain task dependencies"() {
        buildFile.text = """
            ${namedThing}

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
            def elements = container.matching { it.name == "c.txt" }.elements

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
        outputContains("[c.txt]")
        result.assertTaskOrder(":task2", ":task3")

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
