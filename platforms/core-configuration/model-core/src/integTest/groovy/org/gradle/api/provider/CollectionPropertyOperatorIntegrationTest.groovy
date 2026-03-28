/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.provider

class CollectionPropertyOperatorIntegrationTest extends AbstractProviderOperatorIntegrationTest {

    // --------------------------------------------------------
    // ListProperty
    // --------------------------------------------------------

    def "ListProperty addition assignment '#description' works correctly in Groovy DSL"() {
        buildFile """
            abstract class MyTask extends DefaultTask {
                @Internal
                abstract ListProperty<String> getInput()

                @TaskAction
                void run() {
                    println("Result: " + input.get())
                }
            }

            tasks.register("myTask", MyTask) {
                input.set(["a", "b"])
                $statement
            }
        """

        when:
        succeeds "myTask"

        then:
        outputContains(expected)

        where:
        description            | statement                                                                           | expected
        "a += T"               | 'input += "c"'                                                                      | 'Result: [a, b, c]'
        "a = a + T"            | 'input = input + "c"'                                                               | 'Result: [a, b, c]'
        "a += Provider<T>"     | 'input += provider { "c" }'                                                         | 'Result: [a, b, c]'
        "a += Iterable<T>"     | 'input += ["c", "d"]'                                                               | 'Result: [a, b, c, d]'
        "a = a + Iterable<T>"  | 'input = input + ["c", "d"]'                                                        | 'Result: [a, b, c, d]'
        "a += T[]"             | 'input += (["c"] as String[])'                                                      | 'Result: [a, b, c]'
        "a += ListProperty<T>" | 'def other = objects.listProperty(String); other.set(["c"]); input += other'        | 'Result: [a, b, c]'
        "a = a + SetProperty"  | 'def other = objects.setProperty(String); other.set(["c"]); input = input + other'  | 'Result: [a, b, c]'
    }

    def "ListProperty subtraction assignment '#description' works correctly in Groovy DSL"() {
        buildFile """
            abstract class MyTask extends DefaultTask {
                @Internal
                abstract ListProperty<String> getInput()

                @TaskAction
                void run() {
                    println("Result: " + input.get())
                }
            }

            tasks.register("myTask", MyTask) {
                input.set(["a", "b", "c"])
                $statement
            }
        """

        when:
        succeeds "myTask"

        then:
        outputContains(expected)

        where:
        description                    | statement                                                                                | expected
        "a -= T (present)"             | 'input -= "b"'                                                                           | 'Result: [a, c]'
        "a = a - T"                    | 'input = input - "b"'                                                                    | 'Result: [a, c]'
        "a -= T (not present)"         | 'input -= "d"'                                                                           | 'Result: [a, b, c]'
        "a -= Provider<T>"             | 'input -= provider { "b" }'                                                              | 'Result: [a, c]'
        "a -= Iterable<T>"             | 'input -= ["a", "c"]'                                                                    | 'Result: [b]'
        "a = a - Iterable<T>"          | 'input = input - ["a", "c"]'                                                             | 'Result: [b]'
        "a -= ListProperty<T>"         | 'def other = objects.listProperty(String); other.set(["a", "c"]); input -= other'       | 'Result: [b]'
        "a = a - ListProperty<T>"      | 'def other = objects.listProperty(String); other.set(["b", "c"]); input = input - other' | 'Result: [a]'
        "a -= T (removes all occurrences)" | 'input.set(["a", "b", "a"]); input -= "a"'                                            | 'Result: [b]'
    }

    def "ListProperty addition then subtraction works correctly"() {
        buildFile """
            abstract class MyTask extends DefaultTask {
                @Internal
                abstract ListProperty<String> getInput()

                @TaskAction
                void run() {
                    println("Result: " + input.get())
                }
            }

            tasks.register("myTask", MyTask) {
                input.set(["a", "b"])
                input += "c"
                input -= "b"
            }
        """

        when:
        succeeds "myTask"

        then:
        outputContains("Result: [a, c]")
    }

    def "ListProperty addition can be chained"() {
        buildFile """
            abstract class MyTask extends DefaultTask {
                @Internal
                abstract ListProperty<String> getInput()

                @TaskAction
                void run() {
                    println("Result: " + input.get())
                }
            }

            tasks.register("myTask", MyTask) {
                input.set(["a"])
                input += "b"
                input += "c"
                input += ["d", "e"]
            }
        """

        when:
        succeeds "myTask"

        then:
        outputContains("Result: [a, b, c, d, e]")
    }

    def "ListProperty += is lazy: changes to source provider after += are visible at execution time"() {
        buildFile """
            abstract class MyTask extends DefaultTask {
                @Internal
                abstract ListProperty<String> getInput()

                @TaskAction
                void run() {
                    println("Result: " + input.get())
                }
            }

            def other = objects.listProperty(String)
            other.set(["c"])

            tasks.register("myTask", MyTask) {
                input.set(["a", "b"])
                input += other
            }

            // Modify 'other' after += at configuration time — should be visible at execution time
            other.add("d")
        """

        when:
        succeeds "myTask"

        then:
        outputContains("Result: [a, b, c, d]")
    }

    def "ListProperty += snapshot: changes to input after += do not affect the result"() {
        buildFile """
            abstract class MyTask extends DefaultTask {
                @Internal
                abstract ListProperty<String> getInput()

                @TaskAction
                void run() {
                    println("Result: " + input.get())
                }
            }

            tasks.register("myTask", MyTask) {
                input.set(["a", "b"])
                // Capture a snapshot of input before adding "c"
                def snapshot = input + "c"
                // Now add "d" to input — this should NOT affect snapshot
                input.add("d")
                input.set(snapshot)
            }
        """

        when:
        succeeds "myTask"

        then:
        // snapshot captured ["a", "b"] + "c", not the later "d"
        outputContains("Result: [a, b, c]")
    }

    // --------------------------------------------------------
    // SetProperty
    // --------------------------------------------------------

    def "SetProperty addition assignment '#description' works correctly in Groovy DSL"() {
        buildFile """
            abstract class MyTask extends DefaultTask {
                @Internal
                abstract SetProperty<String> getInput()

                @TaskAction
                void run() {
                    println("Result: " + input.get().sort())
                }
            }

            tasks.register("myTask", MyTask) {
                input.set(["a", "b"])
                $statement
            }
        """

        when:
        succeeds "myTask"

        then:
        outputContains(expected)

        where:
        description              | statement                                                                        | expected
        "a += T"                 | 'input += "c"'                                                                   | 'Result: [a, b, c]'
        "a = a + T"              | 'input = input + "c"'                                                            | 'Result: [a, b, c]'
        "a += Provider<T>"       | 'input += provider { "c" }'                                                      | 'Result: [a, b, c]'
        "a += Iterable<T>"       | 'input += ["c", "d"]'                                                            | 'Result: [a, b, c, d]'
        "a += SetProperty<T>"    | 'def other = objects.setProperty(String); other.set(["c"]); input += other'     | 'Result: [a, b, c]'
        "a += duplicate (no-op)" | 'input += "a"'                                                                   | 'Result: [a, b]'
    }

    def "SetProperty subtraction assignment '#description' works correctly in Groovy DSL"() {
        buildFile """
            abstract class MyTask extends DefaultTask {
                @Internal
                abstract SetProperty<String> getInput()

                @TaskAction
                void run() {
                    println("Result: " + input.get().sort())
                }
            }

            tasks.register("myTask", MyTask) {
                input.set(["a", "b", "c"])
                $statement
            }
        """

        when:
        succeeds "myTask"

        then:
        outputContains(expected)

        where:
        description                    | statement                                                                                | expected
        "a -= T (present)"             | 'input -= "b"'                                                                           | 'Result: [a, c]'
        "a = a - T"                    | 'input = input - "b"'                                                                    | 'Result: [a, c]'
        "a -= T (not present)"         | 'input -= "d"'                                                                           | 'Result: [a, b, c]'
        "a -= Iterable<T>"             | 'input -= ["a", "c"]'                                                                    | 'Result: [b]'
        "a -= SetProperty<T>"          | 'def other = objects.setProperty(String); other.set(["a", "c"]); input -= other'       | 'Result: [b]'
        "a = a - SetProperty<T>"       | 'def other = objects.setProperty(String); other.set(["b", "c"]); input = input - other' | 'Result: [a]'
    }
}
