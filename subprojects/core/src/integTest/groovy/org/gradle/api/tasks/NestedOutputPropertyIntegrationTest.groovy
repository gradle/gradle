/*
 * Copyright 2026 Gradle and contributors.
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
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/6619")
class NestedOutputPropertyIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            abstract class OutputBean {
                @OutputFile abstract RegularFileProperty getResult()
            }
            abstract class Generate extends DefaultTask {
                @Nested abstract Property<OutputBean> getBean()

                @TaskAction void generate() {
                    bean.get().result.get().asFile.text = "generated"
                }
            }
            abstract class Consume extends DefaultTask {
                @InputFile @PathSensitive(PathSensitivity.NONE)
                abstract RegularFileProperty getInputFile()
                @OutputFile abstract RegularFileProperty getResult()

                @TaskAction void consume() {
                    result.get().asFile.text = inputFile.get().asFile.text
                }
            }
            def bean = objects.newInstance(OutputBean)
            bean.result.set(layout.buildDirectory.file("generated.txt"))
            def generate = tasks.register("generate", Generate) {
                it.bean.set(bean)
            }
            def consume = tasks.register("consume", Consume) {
                result.set(layout.buildDirectory.file("consumed.txt"))
            }
        """
    }

    def "infers producer through #description with configuration cache #cache"() {
        given:
        buildFile << """
            $prepare
            consume.configure { inputFile.set($connection) }
        """

        when:
        succeeds("consume", cache ? "--configuration-cache" : "--no-configuration-cache")

        then:
        result.assertTasksScheduled(":generate", ":consume")
        file("build/consumed.txt").text == "generated"

        when:
        file("build/generated.txt").delete()
        file("build/consumed.txt").delete()
        succeeds("consume", cache ? "--configuration-cache" : "--no-configuration-cache")

        then:
        file("build/consumed.txt").text == "generated"
        if (cache) {
            outputContains("Reusing configuration cache.")
        }

        where:
        description                  | prepare                                          | connection                                                  | cache
        "nested flatMap"             | ""                                               | "generate.flatMap { it.bean.flatMap { it.result } }"         | false
        "nested flatMap"             | ""                                               | "generate.flatMap { it.bean.flatMap { it.result } }"         | true
        "direct resolution"          | ""                                               | "generate.get().bean.get().result"                          | false
        "direct resolution"          | ""                                               | "generate.get().bean.get().result"                          | true
        "aliased container"          | "def property = generate.get().bean"              | "property.flatMap { it.result }"                           | false
        "aliased container"          | "def property = generate.get().bean"              | "property.flatMap { it.result }"                           | true
        "previously resolved bean"   | "generate.get().bean.get()"                       | "bean.result"                                             | false
        "previously resolved bean"   | "generate.get().bean.get()"                       | "bean.result"                                             | true
    }

    def "infers producer through two nested property levels with configuration cache #cache"() {
        given:
        buildFile << """
            abstract class Outer {
                @Nested abstract Property<OutputBean> getInner()
            }
            abstract class TwoLevels extends DefaultTask {
                @Nested abstract Property<Outer> getOuter()
                @TaskAction void generate() {
                    outer.get().inner.get().result.get().asFile.text = "two levels"
                }
            }
            def outer = objects.newInstance(Outer)
            outer.inner.set(bean)
            def twoLevels = tasks.register("twoLevels", TwoLevels) {
                it.outer.set(outer)
            }
            consume.configure {
                inputFile.set(twoLevels.flatMap { it.outer.flatMap { it.inner.flatMap { it.result } } })
            }
        """

        when:
        succeeds("consume", cache ? "--configuration-cache" : "--no-configuration-cache")

        then:
        result.assertTasksScheduled(":twoLevels", ":consume")
        file("build/consumed.txt").text == "two levels"

        when:
        file("build/generated.txt").delete()
        file("build/consumed.txt").delete()
        succeeds("consume", cache ? "--configuration-cache" : "--no-configuration-cache")

        then:
        file("build/consumed.txt").text == "two levels"
        if (cache) {
            outputContains("Reusing configuration cache.")
        }

        where:
        cache << [false, true]
    }

    def "input-only beans remain shareable with configuration cache #cache"() {
        given:
        buildFile << """
            abstract class Inputs {
                @Input abstract Property<String> getMessage()
            }
            abstract class Print extends DefaultTask {
                @Nested abstract Property<Inputs> getBean()
                @TaskAction void printMessage() { println(bean.get().message.get()) }
            }
            def shared = objects.newInstance(Inputs)
            shared.message.set("shared input")
            tasks.register("a", Print) { it.bean.set(shared) }
            tasks.register("b", Print) { it.bean.set(shared) }
        """

        when:
        succeeds("a", "b", cache ? "--configuration-cache" : "--no-configuration-cache")

        then:
        output.count("shared input") == 2

        when:
        succeeds("a", "b", cache ? "--configuration-cache" : "--no-configuration-cache")

        then:
        output.count("shared input") == 2
        if (cache) {
            outputContains("Reusing configuration cache.")
        }

        where:
        cache << [false, true]
    }

    def "conflicting output owners are rejected in either order #order with configuration cache #cache"() {
        given:
        buildFile << """
            tasks.register("other", Generate) { it.bean.set(bean) }
            tasks.named("${order[0]}").get().bean.get()
            tasks.named("${order[1]}").get().bean.get()
            consume.configure { inputFile.set(bean.result) }
        """

        when:
        fails("consume", cache ? "--configuration-cache" : "--no-configuration-cache")

        then:
        failure.assertHasCause("Nested output declared by task ':${order[0]}' property 'bean' has more than one producing task: task ':${order[0]}' and task ':${order[1]}'. Use a separate output bean for each task.")

        where:
        order                   | cache
        ["generate", "other"]   | false
        ["other", "generate"]   | false
        ["generate", "other"]   | true
        ["other", "generate"]   | true
    }

    def "ordinary bean reads do not prevent replacing the declaration with configuration cache #cache"() {
        given:
        buildFile << """
            generate.get().bean.get()
            def replacement = objects.newInstance(OutputBean)
            replacement.result.set(layout.buildDirectory.file("replacement.txt"))
            generate.get().bean.set(replacement)
            consume.configure { inputFile.set(generate.flatMap { it.bean.flatMap { it.result } }) }
        """

        when:
        succeeds("consume", cache ? "--configuration-cache" : "--no-configuration-cache")

        then:
        file("build/replacement.txt").text == "generated"
        !file("build/generated.txt").exists()
        file("build/consumed.txt").text == "generated"

        where:
        cache << [false, true]
    }

    def "explicit finalization retains the resolved bean owner with configuration cache #cache"() {
        given:
        buildFile << """
            def output = generate.get().bean.get().result
            generate.get().bean.finalizeValue()
            consume.configure { inputFile.set(output) }
        """

        expect:
        succeeds("consume", cache ? "--configuration-cache" : "--no-configuration-cache")
        file("build/consumed.txt").text == "generated"

        where:
        cache << [false, true]
    }

    def "provider-created output structure is retained after dependency discovery with configuration cache #cache"() {
        given:
        buildFile << """
            def objects = objects
            def output = layout.buildDirectory.file("computed.txt")
            generate.configure {
                it.bean.set(providers.provider {
                    def computed = objects.newInstance(OutputBean)
                    computed.result.set(output)
                    computed
                })
            }
            consume.configure { inputFile.set(generate.flatMap { it.bean.flatMap { it.result } }) }
        """

        when:
        succeeds("consume", cache ? "--configuration-cache" : "--no-configuration-cache")

        then:
        file("build/consumed.txt").text == "generated"

        when:
        file("build/computed.txt").delete()
        file("build/consumed.txt").delete()
        succeeds("consume", cache ? "--configuration-cache" : "--no-configuration-cache")

        then:
        file("build/consumed.txt").text == "generated"
        if (cache) {
            outputContains("Reusing configuration cache.")
        }

        where:
        cache << [false, true]
    }

    def "final nested property getters establish declaration context with configuration cache #cache"() {
        given:
        buildFile << """
            abstract class FinalGetterGenerate extends Generate {
                private final Property<OutputBean> nestedBean
                @Inject FinalGetterGenerate(ObjectFactory objects) {
                    nestedBean = objects.property(OutputBean)
                }
                @Nested @Override final Property<OutputBean> getBean() { nestedBean }
            }
            def finalGetter = tasks.register("finalGetter", FinalGetterGenerate) { it.bean.set(bean) }
            consume.configure { inputFile.set(finalGetter.flatMap { it.bean.flatMap { it.result } }) }
        """

        expect:
        succeeds("consume", cache ? "--configuration-cache" : "--no-configuration-cache")
        file("build/consumed.txt").text == "generated"

        where:
        cache << [false, true]
    }

    def "internal properties do not confer output ownership"() {
        given:
        buildFile << """
            abstract class InternalBeanTask extends DefaultTask {
                @Internal abstract Property<OutputBean> getBean()
            }
            def internal = tasks.register("internal", InternalBeanTask) { it.bean.set(bean) }
            consume.configure { inputFile.set(internal.flatMap { it.bean.flatMap { it.result } }) }
        """

        when:
        fails("consume")

        then:
        failure.assertHasCause("Property 'result' is declared as an output property of an object with type OutputBean but does not have a task associated with it.")
    }

    def "assignment and container access do not evaluate the bean supplier"() {
        given:
        buildFile << """
            generate.get().bean.set(providers.provider { throw new IllegalStateException("bean was queried") })
            tasks.register("checkAssignment") { doLast { println("configured without querying") } }
        """

        expect:
        succeeds("checkAssignment")
        outputContains("configured without querying")
    }

    def "polymorphic nested output owners are rejected without prohibiting shared input wrappers"() {
        given:
        buildFile << """
            abstract class Wrapper {
                @Nested abstract Property<Object> getValue()
            }
            abstract class Wrapped extends DefaultTask {
                @Nested abstract Property<Wrapper> getWrapper()
            }
            def wrapper = objects.newInstance(Wrapper)
            wrapper.value.set(bean)
            tasks.register("a", Wrapped) { it.wrapper.set(wrapper) }.get().wrapper.get()
            tasks.register("b", Wrapped) { it.wrapper.set(wrapper) }.get().wrapper.get()
            wrapper.value.get()
            consume.configure { inputFile.set(bean.result) }
        """

        when:
        fails("consume")

        then:
        failure.assertHasCause("Nested output declared by task ':a' property 'wrapper' has more than one producing task: task ':a' and task ':b'. Use a separate output bean for each task.")
    }

    def "resolving the inner bean before its parent is attached retains the ownership chain with configuration cache #cache"() {
        given:
        buildFile << """
            abstract class Outer {
                @Nested abstract Property<OutputBean> getInner()
            }
            abstract class TwoLevels extends DefaultTask {
                @Nested abstract Property<Outer> getOuter()
                @TaskAction void generate() {
                    outer.get().inner.get().result.get().asFile.text = "early inner"
                }
            }
            def outer = objects.newInstance(Outer)
            outer.inner.set(bean)
            def output = outer.inner.get().result
            def producer = tasks.register("producer", TwoLevels) { it.outer.set(outer) }
            producer.get().outer.get()
            consume.configure { inputFile.set(output) }
        """

        expect:
        succeeds("consume", cache ? "--configuration-cache" : "--no-configuration-cache")
        file("build/consumed.txt").text == "early inner"

        where:
        cache << [false, true]
    }

    def "nested output structure cannot be replaced during execution with configuration cache #cache"() {
        given:
        buildFile << """
            abstract class ReplacingGenerate extends Generate {
                @Internal abstract Property<OutputBean> getReplacement()
                @Override @TaskAction void generate() {
                    bean.set(replacement)
                    super.generate()
                }
            }
            def replacement = objects.newInstance(OutputBean)
            replacement.result.set(layout.buildDirectory.file("replacement.txt"))
            def producer = tasks.register("producer", ReplacingGenerate) {
                it.bean.set(bean)
                it.replacement.set(replacement)
            }
            consume.configure { inputFile.set(producer.flatMap { it.bean.flatMap { it.result } }) }
        """

        when:
        fails("consume", cache ? "--configuration-cache" : "--no-configuration-cache")

        then:
        failure.assertHasCause("The value for task ':producer' property 'bean' is final and cannot be changed any further.")

        where:
        cache << [false, true]
    }

    def "configuration-derived structure can combine task state providers with configuration cache #cache"() {
        given:
        buildFile << """
            def a = tasks.register("a")
            def b = tasks.register("b")
            generate.configure {
                it.bean.set(a.zip(b) { first, second -> bean })
            }
            consume.configure { inputFile.set(generate.flatMap { it.bean.flatMap { it.result } }) }
        """

        expect:
        succeeds("consume", cache ? "--configuration-cache" : "--no-configuration-cache")
        file("build/consumed.txt").text == "generated"

        where:
        cache << [false, true]
    }

    def "shared output declarations without inferred consumers are deprecated with configuration cache #cache"() {
        given:
        buildFile << """
            tasks.register("other", Generate) { it.bean.set(bean) }
        """
        executer.expectDocumentedDeprecationWarning(
            "Declaring the same nested output bean on multiple tasks. This behavior has been deprecated. " +
                "This will fail with an error in Gradle 10. " +
                "Nested output declared by task ':generate' property 'bean' has more than one producing task: task ':generate' and task ':other'. Use a separate output bean for each task. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#nested_output_ownership"
        )

        expect:
        succeeds("generate", "other", cache ? "--configuration-cache" : "--no-configuration-cache")
        file("build/generated.txt").text == "generated"

        where:
        cache << [false, true]
    }

    def "one task may expose the same output bean through several declarations with configuration cache #cache"() {
        given:
        buildFile << """
            abstract class AliasedGenerate extends Generate {
                @Nested abstract Property<OutputBean> getAlias()
            }
            def producer = tasks.register("producer", AliasedGenerate) {
                it.bean.set(bean)
                it.alias.set(bean)
            }
            producer.get().bean.get()
            producer.get().alias.get()
            consume.configure { inputFile.set(producer.flatMap { it.alias.flatMap { it.result } }) }
        """

        when:
        succeeds("consume", cache ? "--configuration-cache" : "--no-configuration-cache")

        then:
        result.assertTasksScheduled(":producer", ":consume")
        file("build/consumed.txt").text == "generated"

        where:
        cache << [false, true]
    }

    def "stale ownership from a replaced declaration is discarded with configuration cache #cache"() {
        given:
        buildFile << """
            generate.get().bean.get()
            def replacement = objects.newInstance(OutputBean)
            replacement.result.set(layout.buildDirectory.file("replacement.txt"))
            generate.get().bean.set(replacement)
            def other = tasks.register("other", Generate) { it.bean.set(bean) }
            consume.configure { inputFile.set(other.flatMap { it.bean.flatMap { it.result } }) }
        """

        when:
        succeeds("consume", cache ? "--configuration-cache" : "--no-configuration-cache")

        then:
        result.assertTasksScheduled(":other", ":consume")
        file("build/consumed.txt").text == "generated"
        !file("build/replacement.txt").exists()

        where:
        cache << [false, true]
    }

    def "ownership crosses direct nested getters and nested properties with configuration cache #cache"() {
        given:
        buildFile << """
            abstract class Outer {
                @Nested abstract OutputBean getInner()
            }
            abstract class MixedGenerate extends DefaultTask {
                @Nested abstract Property<Outer> getOuter()
                @TaskAction void generate() {
                    outer.get().inner.result.get().asFile.text = "mixed levels"
                }
            }
            def outer = objects.newInstance(Outer)
            outer.inner.result.set(layout.buildDirectory.file("mixed.txt"))
            def producer = tasks.register("producer", MixedGenerate) { it.outer.set(outer) }
            consume.configure { inputFile.set(producer.flatMap { it.outer.flatMap { it.inner.result } }) }
        """

        when:
        succeeds("consume", cache ? "--configuration-cache" : "--no-configuration-cache")

        then:
        result.assertTasksScheduled(":producer", ":consume")
        file("build/consumed.txt").text == "mixed levels"

        when:
        file("build/mixed.txt").delete()
        file("build/consumed.txt").delete()
        succeeds("consume", cache ? "--configuration-cache" : "--no-configuration-cache")

        then:
        file("build/consumed.txt").text == "mixed levels"
        if (cache) {
            outputContains("Reusing configuration cache.")
        }

        where:
        cache << [false, true]
    }

    def "plain nested beans still register output locations with configuration cache #cache"() {
        given:
        buildFile << """
            class PlainBean {
                @OutputFile File result
            }
            abstract class PlainGenerate extends DefaultTask {
                @Nested abstract Property<PlainBean> getBean()
                @TaskAction void generate() { bean.get().result.text = "plain" }
            }
            tasks.register("plain", PlainGenerate) {
                it.bean.set(new PlainBean(result: layout.buildDirectory.file("plain.txt").get().asFile))
            }
        """

        when:
        succeeds("plain", cache ? "--configuration-cache" : "--no-configuration-cache")

        then:
        file("build/plain.txt").text == "plain"

        when:
        succeeds("plain", cache ? "--configuration-cache" : "--no-configuration-cache")

        then:
        skipped(":plain")
        if (cache) {
            outputContains("Reusing configuration cache.")
        }

        where:
        cache << [false, true]
    }

    def "flatMap still uses the producer of the returned provider with configuration cache #cache"() {
        given:
        buildFile << """
            def otherBean = objects.newInstance(OutputBean)
            otherBean.result.set(layout.buildDirectory.file("other.txt"))
            def other = tasks.register("other", Generate) { it.bean.set(otherBean) }
            consume.configure {
                inputFile.set(generate.flatMap { it.bean.flatMap { other.flatMap { it.bean.flatMap { it.result } } } })
            }
        """

        when:
        succeeds("consume", cache ? "--configuration-cache" : "--no-configuration-cache")

        then:
        result.assertTasksScheduled(":other", ":consume")
        file("build/consumed.txt").text == "generated"

        where:
        cache << [false, true]
    }

    def "nested input leaves retain their own producers with configuration cache #cache"() {
        given:
        buildFile << """
            abstract class InputOutputBean extends OutputBean {
                @InputFile @PathSensitive(PathSensitivity.NONE)
                abstract RegularFileProperty getSource()
            }
            def upstreamBean = objects.newInstance(OutputBean)
            upstreamBean.result.set(layout.buildDirectory.file("upstream.txt"))
            def upstream = tasks.register("upstream", Generate) { it.bean.set(upstreamBean) }
            def combined = objects.newInstance(InputOutputBean)
            combined.source.set(upstream.flatMap { it.bean.flatMap { it.result } })
            combined.result.set(layout.buildDirectory.file("generated.txt"))
            generate.configure { it.bean.set(combined) }
            consume.configure { inputFile.set(generate.flatMap { it.bean.flatMap { it.result } }) }
        """

        when:
        succeeds("consume", cache ? "--configuration-cache" : "--no-configuration-cache")

        then:
        result.assertTasksScheduled(":upstream", ":generate", ":consume")
        file("build/consumed.txt").text == "generated"

        where:
        cache << [false, true]
    }

    def "resolving output structure does not allow reading generated content before its producer runs with configuration cache #cache"() {
        given:
        buildFile << """
            abstract class Source extends DefaultTask {
                @OutputFile abstract RegularFileProperty getResult()
                @TaskAction void generate() { result.get().asFile.text = "source" }
            }
            def source = tasks.register("source", Source) { result.set(layout.buildDirectory.file("source.txt")) }
            generate.configure {
                it.bean.set(source.flatMap { it.result }.map { throw new IllegalStateException("generated content was read") })
            }
            consume.configure { inputFile.set(generate.flatMap { it.bean.flatMap { it.result } }) }
        """

        when:
        fails("consume", cache ? "--configuration-cache" : "--no-configuration-cache")

        then:
        failure.assertHasCause("Querying the mapped value of flatmap(provider(task 'source', class Source)) before task ':source' has completed is not supported")
        failure.assertHasNoCause("generated content was read")

        where:
        cache << [false, true]
    }
}
